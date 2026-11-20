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
- **单线程基线（2026-11-20 补测）**：`py -3.9 scripts/w6/bench_http.py --url .../api/stat/today --token $TOKEN -c 1 -n 20 --label smoke` → n=20 avg=10.4ms p50=10.2 p95=13.2 p99=14.2 max=14.2 fail=0 qps=81.9。
- **并发下的真实数字**见本文件 W6 段组 1（c=100 时 stat/today P95=69.4ms）与组 2（QPS 拐点）。W5 当时留空的组 1 已由 W6 实测替换。
- **降级表现**：`docker stop study-redis` 后接口返回 200，降级直查 MySQL；W6 又加了 500ms 超时 + 30 秒本地短路，单次 RT 从 8.09s 降到 1.03s（首次）/ 0.52s（后续），见 decisions D-50。

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
  - **实测**：满足预期，避免了僵尸数据与前端请求挂起。

---

## W6 阶段：容器化全栈实测（2026-11-20）

### 测试环境

- 机器：ThinkBook 16 / i5-13500H / 12 核 / 15.5GB / Windows 11 Home China build 26200；Docker 29.9.3（containerd）
- 应用：study-helper/app:v1.0（M9 修复后 IMAGE ID **3efd6a1e4698**，684MB / 磁盘 224MB），`-Xmx512m -XX:MaxMetaspaceSize=192m`，非 root（uid=999），单实例，四个服务全在 compose 内
- 数据量：question 200（Java 120 / MySQL 40 / Redis 40）；压测账号 demo 的 user_answer_record 若干、ai_report 十余份
- 压测工具：自研 `scripts/w6/bench_http.py`（标准库 http.client，连接复用，threading），**不是 JMeter**；客户端与服务同机，因此 QPS 只代表单实例下限
- 环境快照：`scripts/w6/env_snapshot.sh`（机器/JDK/镜像 ID/数据量/RabbitMQ 水位一次性打出）

### 组 1：接口 RT 与 P95（c=100，d=30s）

| 接口 | n | avg | P50 | P95 | P99 | max | QPS | fail |
|---|---|---|---|---|---|---|---|---|
| /api/question/list?page=1&size=10 | 95819 | 31.1 | 27.6 | **64.2** | 95.1 | 320.6 | 3190.3 | 0 |
| /api/wrong/hot | 134061 | 22.2 | 18.7 | **51.2** | 74.2 | 195.0 | 4463.1 | 0 |
| /api/stat/today | 92847 | 32.2 | 27.9 | **69.4** | 100.5 | 392.6 | 3091.5 | 0 |

命令：

```bash
py -3.9 scripts/w6/bench_http.py --url "http://localhost:8080/api/question/list?page=1&size=10" --token "$TOKEN" -c 100 -d 30 --label q-list
py -3.9 scripts/w6/bench_http.py --url "http://localhost:8080/api/wrong/hot"  --token "$TOKEN" -c 100 -d 30 --label wrong-hot
py -3.9 scripts/w6/bench_http.py --url "http://localhost:8080/api/stat/today" --token "$TOKEN" -c 100 -d 30 --label stat-today
```

结论：
- 三者 fail 全 0。QPS 最高的是 wrong/hot（纯 Redis 字符串读，4463），stat/today 反而最慢（一次请求读 HASH 计数 + 打卡位图，两趟 Redis 加日期运算）。
- 单线程 0.9ms → c=100 时 22ms，不是代码变慢，是排队：延迟换吞吐。
- P95/avg ≈ 2 属正常；max 320 / 392ms 是 512m 堆的 GC 与排队尾，组 2 进一步印证。

### 组 2：QPS 拐点（/api/stat/today，每档 20s）

| c | n | QPS | avg | P95 | P99 | max | fail |
|---|---|---|---|---|---|---|---|
| 50 | 72971 | **3645.0** | 13.7 | 30.4 | 44.7 | 117.8 | 0 |
| 100 | 62105 | 3098.7 | 31.9 | 71.7 | 109.5 | 309.7 | 0 |
| 150 | 59263 | 2951.9 | 49.5 | 116.4 | 178.7 | 620.0 | 0 |
| 200 | 61174 | 3047.3 | 61.3 | 143.2 | 222.4 | 1372.9 | 0 |
| 250 | 68229 | 3396.8 | 65.9 | 147.7 | 236.9 | 1692.5 | 0 |
| 300 | 63286 | 3145.6 | 79.5 | 195.2 | 324.5 | 2382.8 | 0 |

命令：

```bash
for c in 50 100 150 200 250 300; do
  py -3.9 scripts/w6/bench_http.py --url "http://localhost:8080/api/stat/today" --token "$TOKEN" -c $c -d 20 --label "knee-c$c"
  sleep 5
done
# 同时另开窗口：while true; do docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} MEM={{.MemUsage}}' study-app; sleep 3; done
```

结论：
- 按「错误率 > 1% 或 P95 > 500ms」的停机规则，六档都没触发，fail 全 0，最高 P95 195ms（c=300）。
- 按吞吐看，**拐点在 c≈50**：那里已经是 3645 QPS，之后 QPS 在 2950–3400 平台不动，而 avg/P95 随并发线性上升——典型的「吞吐已饱和，再压只是排队」。
- 瓶颈判断：压测期间容器 CPU 峰值 367%–473%（≈4.7 核 / 12 核）**没有打满**，所以不是算力瓶颈；同时客户端与服务同机，Python 线程模型自身也参与争抢。结论写「单实例吞吐约 3000–3600 QPS，瓶颈在排队与单机客户端，不是 SQL」。
- max 从 117ms 涨到 2.38s（c=300），长尾来自 512m 堆 GC。

### 组 3：缓存命中率（/api/wrong/hot，n=1000，c=1）

| 组 | n | avg | P50 | P95 | P99 | max | QPS | redis hits | **misses** | 缓存命中率 |
|---|---|---|---|---|---|---|---|---|---|---|
| 冷（DEL key 后） | 1000 | 1.0 | 0.8 | 1.2 | 3.3 | 25.5 | 1005.4 | 2006 | **2** | **99.80%** |
| 热（key 在） | 1000 | 0.9 | 0.7 | 1.7 | 2.6 | 22.5 | 1056.4 | 2006 | **0** | **100%** |

命令：`bash scripts/w6/bench_cache_hit.sh "$TOKEN" 1000`

口径（重要，别写错）：
- redis `hits` 里含每次请求的 token 校验 GET（每次都命中），所以全口径命中率必然接近 100%，**不能当作缓存成效**。
- 干净口径是 `keyspace_misses`：miss 只可能来自缓存 key 本身。冷组 2 次（首次回源 + 回填竞态），热组 0 次。
- MySQL `Com_select` **不能**当回源数：实例上存在约每 3 秒一次的背景 SELECT（空闲 3 秒也会 +1，与请求数无关），实测热组显示 3 次是噪声。
- TTL 检查：EXISTS=1、TTL=1633s（30 分钟窗口内未过期）。

结论：Cache-Aside 生效，冷组只回源一次；但 200 题规模下回源 SQL 本身只要几毫秒，所以冷热两组的 avg 差别只有 0.1ms——真正拉开差距的是 W3 那次 c=20 的对比（634ms → 21ms），本轮如实记录「小数据量下收益不明显」。

### 组 4：索引收益（影子表 question_idx_test，6400 行 = 200 × 32）

分页 SQL（09 文档口径）与计数 SQL（暴露全扫成本）：

```sql
SELECT id,subject,chapter,content FROM question_idx_test WHERE subject='Java' AND chapter='集合' AND difficulty=2 ORDER BY id LIMIT 10;
SELECT COUNT(*) FROM question_idx_test WHERE subject='Java' AND chapter='集合' AND difficulty=2;
```

| 状态 | 计数 type | 计数 rows | 实际扫描 | 计数实际耗时 | 分页 type/rows | 分页实际耗时 | Extra |
|---|---|---|---|---|---|---|---|
| A 无索引 | ALL | 6326 | 6400 行 | **1.28ms** | index / 10（走 PRIMARY） | 0.038ms | Using where |
| B 两列 (subject,chapter) | ref | 1280 | 1280 行回表 | **2.17ms** | ref / 1280 | 0.067ms | Using where |
| C 三列 (+difficulty) | ref | 448 | 448 行 | **0.10ms** | ref / 448 | 0.041ms | Using index（覆盖索引） |

命令：`bash scripts/w6/bench_index.sh`（自动建影子表、灌 6400 行、三状态 EXPLAIN + EXPLAIN ANALYZE、列出索引清单）

结论（如实写，不修饰）：
- **6400 行规模下，两列索引比全表扫还慢**（2.17ms vs 1.28ms）：选择性只有 1280/6400，1280 次回表的成本超过了 6400 行顺序扫。
- 三列索引才真正生效：rows 448、计数走覆盖索引、0.10ms，比全扫快约 12 倍、比两列快约 21 倍。
- 分页查询在任何状态下都可能走 PRIMARY（`ORDER BY id LIMIT 10` 只需扫 68 行），所以分页看不出差别，**必须配 COUNT 版本**才能暴露全扫成本——这是 W2 那版只测分页留下的盲区。

### 组 5：事务正确性

```bash
docker exec study-mysql mysql -uroot -p*** study_helper -e "SELECT (SELECT COUNT(*) FROM user_answer_record) a,(SELECT COUNT(*) FROM wrong_book) b,(SELECT COUNT(*) FROM ai_report) c;"
mvn -o -q -pl project-a -Dtest=TransactionRollbackIT test
```

| 方法 | 断言 | 结果 |
|---|---|---|
| submitCommitsAll | 答题记录 +1、错题本 +1、ai_report 不变 | 通过 |
| runtimeExceptionRollsBack | 事务内抛异常（数据库触发器注入），三表 count 完全不变 | 通过 |
| swallowedExceptionStillCommits | 方法返回后调用方抛异常并吞掉，记录仍在（证明事务边界在方法上） | 通过 |

- 输出：`Tests run: 3, Failures: 0, Errors: 0`；全仓 `mvn test` 共 7 个用例全绿（3 个事务失效实验 + 3 个原子性断言 + 1 个统计口径回归）。
- 注入方式：在 `wrong_book` 上建 BEFORE INSERT 触发器对固定测试用户 `SIGNAL SQLSTATE '45000'`，不改业务代码；测试类跑完自清理（触发器、记录、临时用户、Redis key）。

### 组 6：MQ 可靠性（五场景）

命令：`bash scripts/w6/bench_mq_reliability.sh`（五场景串行，约 6 分钟；每场景一个独立账号绕开 60 秒生成限流）

| 场景 | 怎么造 | 实测结果 | 消息最终去向 |
|---|---|---|---|
| S1 投递失败 | 停 broker 后 `POST /api/report/generate` | HTTP 200，status=**2**，error_msg=`publish_failed` | 消息没进 MQ，本地模板降级落库，无 status=0 僵尸 |
| S2 消费异常重试 | MyISAM 计数表 + `ai_report` BEFORE UPDATE 触发器，前 4 次 UPDATE 失败 | retry=1/2/3/4（间隔 17s），第 4 次写模板 status=**2**（template-v1，error_msg 以 `retry_exhausted` 开头） | **DLQ messages=1**（审计消息），主队列清空 |
| S3 消费者被打断 | 生成后 1 秒 `docker kill study-app`，10 秒后重启 | 队列 messages=1（未 ACK 回队列），重启后重新消费，最终 status=1（另有轮次为 2），该 requestId **只有 1 行** | 消息 re-requeue 后由新实例消费，无重复行 |
| S4 重复投递 | 用 management API 重发同一 requestId 的消息 | status/tokenCost `1/841 → 1/841` 不变，本次幂等拦截日志 **1 条** | 被 status!=0 的幂等守卫直接 ACK 丢弃 |
| S5 队列深度 | 先停消费者，投 300 条（reportId 不存在），再启消费者 | 投完积压 **282** 条，清空用时 **4 秒**（≈70 条/秒） | 积压被消费端一次性清空（幂等守卫 ACK） |

关键实现细节（面试可讲）：
- S2 的标记表必须是 **MyISAM**：InnoDB 表在触发器里的写会随 `SIGNAL` 一起回滚，计数器永远减不动，测试会变成「永远失败」。
- 重试次数取 4 = 3 次重试 + 第 4 次投递；第 4 次进 catch 走 fallback，它的 UPDATE 是第 5 次，必须让它成功。
- 顺带修掉一个真 bug：`error_msg` 是 varchar(255)，重试耗尽时把 MyBatis 的整段异常（含 SQL 文本）塞进去会 `Data too long`，导致模板都写不进去、留下 status=0 僵尸行。`ReportConsumer.trimMsg()` 裁到 240 字符（D-51）。

### 哪些数字能进简历

| 能写 | 限定词（必须一起写） |
|---|---|
| P95 51–69ms | 单实例、-Xmx512m、100 并发、30 秒、自研脚本单机客户端 |
| 吞吐 3000–3600 QPS 平台 | 同上，且注明 c≈50 即饱和 |
| 缓存命中 99.8% / 100% | c=1、n=1000、misses 口径 |
| 索引 0.10ms vs 1.28ms | 6400 行影子表、COUNT 查询、三列覆盖索引 |

不许写：单次 curl 的耗时、JMeter 名义下的自研数字、没写数据量的耗时、任何「预期」估算。