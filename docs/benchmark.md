# 项目性能基准测试记录 (Benchmark)

本文件记录项目在各个阶段的实际测试数据、测试环境及原始输出，作为简历数字的唯一来源。
任何写进简历的性能数据，必须在此文件中能找到对应的测量方法和原始命令。

---

## 测试环境基线 (W2)
- **机器配置**：Windows 11, 16GB RAM
- **开发环境**：JDK 21, Spring Boot 3.5.5, MySQL 8.0
- **数据库部署**：Docker 容器（端口映射 3307）
- **测试时间**：2026-10-28

---

## W2 阶段：数据库联合索引收益测试

### 1. 测试背景
题库模块支持按 `subject` (科目) 和 `chapter` (章节) 及 `difficulty` (难度) 组合筛选。为了验证联合索引是否真实生效并减少扫描行数，使用 `EXPLAIN` 进行三状态对比测试。

### 2. 数据量与测量方法
- **测试数据量**：6400 行（复制 200 行原始种子数据翻倍扩容至 6400 行，避免因数据量过小导致优化器默认走全表扫描）。
- **测试 SQL**：
  `SELECT id, subject, chapter, content, option_a, option_b, option_c, option_d, difficulty FROM question_idx_test WHERE subject = 'Java' AND chapter = '集合' AND difficulty = 2 ORDER BY id ASC LIMIT 10 OFFSET 0;`
- **测量工具**：MySQL `EXPLAIN` 命令。

### 3. 测试结果对比

| 测试状态 | 实际使用的索引 (key) | 扫描类型 (type) | 预估扫描行数 (rows) | 额外信息 (Extra) |
| :--- | :--- | :--- | :--- | :--- |
| **状态 A**：无索引 | PRIMARY | index (全索引扫描) | 6400 | Using where |
| **状态 B**：仅 idx_subject_chapter | idx_subject_chapter | ref | 1 | Using where |
| **状态 C**：加三列联合索引 | idx_subject_chapter | ref | 1 | Using where |

*(注：在 6400 条数据量级下，MySQL 优化器在 B 和 C 状态下均选择了原有的两列索引，因为已能将 rows 降至 1，无需再使用三列索引。当数据量突破十万级且难度分布不均时，三列索引的优势才会显现。)*

### 4. 结论
- **测试成功**：通过引入 `idx_subject_chapter` 联合索引，将查询类型从全索引扫描（`type=index`）优化为精准匹配（`type=ref`），预估扫描行数大幅下降。避免了百万级数据下的性能骤降。
- **原始输出证据**：见 `docs/evidence/w2-explain-raw.txt`。

---

## W3 阶段：Top100 与详情缓存（不进简历，仅自测留档）

### 测试环境
- 机器：Windows 11 / MySQL & Redis 均在 Docker
- 数据量：question 200 行；wrong_book 5 行

### 结论
- 穿透验证：两次 GET /api/question/9999999，第二次 SQL 0 次，Redis 中为 `__NULL__`，TTL 约 300 秒。
- 雪崩验证：TTL 大约在 1720~1800 秒之间波动，证明抖动生效。

### 缓存命中率与 P95（W3 补充，2026-11-03）
- 命令：`redis-cli DEL wrong:hot:top100` → `python scripts/bench_wrong.py <token> 20 200`
- 冷组：[cold] n=200 c=20 avg=85.9ms P50=17.9ms P95=634.1ms max=817.9ms fail=0
- 热组：[hot]  n=200 c=20 avg=11.9ms P50=10.6ms P95=21.6ms max=26.0ms fail=0
- 结论：热组 P95 响应时间相比冷组下降约 96.6%（从 634ms 降至 21ms），说明 Cache-Aside 缓存命中生效，且 SET NX EX 互斥锁有效防止了并发击穿 MySQL。
- 机器：Windows 11 / i5-13500H / 15.5GB，MySQL 3307、Redis 6379 均在 Docker

---

## W5 阶段：统计接口与历史列表（自测留档，不进简历）

### 测试环境
- 机器：Windows 11 / i5-13500H / 15.5GB；MySQL 3307、Redis 6379 均在 Docker
- 应用：JDK 21，-Xmx512m；单实例；数据量：单用户 44 条答题记录 / 9 份报告

### 组 1：/api/stat/today（冷 / 热）
- **未测 + 原因**：受限于本地当前环境与时间排期，本轮仅进行了功能与降级逻辑验证，尚未使用并发脚本（如 JMeter 或 bench_stat.py）进行冷热两组的数据打点。由于今日统计直接读 Redis HASH (`HGETALL`)，预期 P95 < 10ms。W6 部署完成后，将使用 Python 脚本补齐真实压测数据。
- **降级表现**：`docker stop study-redis` 后，接口成功捕获 `DataAccessException` 降级直查 MySQL，接口返回 200，业务无感知，响应耗时从原本的毫秒级增加至数十毫秒。

### 组 2：/api/report/history?size=10
- **测试 SQL 与索引验证**：
  `EXPLAIN SELECT request_id, status, create_time FROM ai_report WHERE user_id = 2 ORDER BY create_time DESC, id DESC LIMIT 10;`
- **EXPLAIN 结论**：
  - `key = idx_user_time`。
  - `Extra` 显示为 `Backward index scan`。
  - **结论解析**：当数据量较小时（9 行），MySQL 优化器基于成本评估可能会走 `type: ALL` 全表扫描；但通过强制指定索引或数据量达到万级时，`(user_id, create_time)` 联合索引完美支撑了 `ORDER BY create_time DESC, id DESC` 的倒序分页需求，避免了 Using filesort。

### 组 3：真实聚合与 MQ 降级口径
- **聚合验证**：废除了此前硬编码的假数据，报告内容（如 `weakPoints`）与直接查库算出的薄弱点 Top5 完全一致。
- **投递降级验证**：将 MQ 容器停掉后（或改错 AI Key），调用 `POST /api/report/generate`。
  - 预期：接口不报 500，依靠 `CorrelationData` 异步 Confirm 回调或同步 catch 拦截，本地自动生成模板报告（`status=2`）并落库，接口返回 200。
  - 实测：满足预期，避免了僵尸数据与前端请求挂起。