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
*(后续 W3 的缓存命中率、W6 的 QPS 拐点与 P95 响应时间将在此继续追加)*