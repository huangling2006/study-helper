# W6 六组实测原始数据（未整理，最后统一并入 docs/benchmark.md）

记录时间：2026-11-20 21:29–21:47
机器：i5-13500H / 12 核 / 15.5GB / Windows 11 Home China build 26200
Docker：server 29.9.3；镜像 study-helper/app:v1.0（5019e47a631d，684MB）
JVM：JAVA_TOOL_OPTIONS=-Xmx512m -XX:MaxMetaspaceSize=192m（MaxHeapSize=536870912 已验证）
数据量：question 200（Java 120 / MySQL 40 / Redis 40）、user 1、user_answer_record 12、wrong_book 4、ai_report 2
压测口径：自研 Python 脚本（http.client，keep-alive），单机客户端，客户端与服务同机
容器资源：mem_limit 768m，CPU 未限额；实测容器 CPU 峰值 367%–473%（≈4.7 核 / 12 核）

---

## 组 1：接口 RT 与 P95（c=100，d=30s）

| 接口 | n | avg | P50 | P95 | P99 | max | QPS | fail |
|---|---|---|---|---|---|---|---|---|
| /api/question/list?page=1&size=10 | 95819 | 31.1 | 27.6 | 64.2 | 95.1 | 320.6 | 3190.3 | 0 |
| /api/wrong/hot | 134061 | 22.2 | 18.7 | 51.2 | 74.2 | 195.0 | 4463.1 | 0 |
| /api/stat/today | 92847 | 32.2 | 27.9 | 69.4 | 100.5 | 392.6 | 3091.5 | 0 |

原始行：
```
[q-list]    n=95819  c=100 dur=30.0s avg=31.1ms p50=27.6 p95=64.2 p99=95.1 max=320.6 fail=0 qps=3190.3 codes={200:95819}
[wrong-hot] n=134061 c=100 dur=30.0s avg=22.2ms p50=18.7 p95=51.2 p99=74.2 max=195.0 fail=0 qps=4463.1 codes={200:134061}
[stat-today] n=92847 c=100 dur=30.0s avg=32.2ms p50=27.9 p95=69.4 p99=100.5 max=392.6 fail=0 qps=3091.5 codes={200:92847}
```

结论要点：
- 三者都 fail=0；QPS 最高的是 wrong/hot（纯 Redis 字符串读），stat/today 最慢（一次请求读 HASH 计数 + 打卡位图两趟 Redis，外加日期运算）。
- 单线程 0.9ms → c=100 时 22ms，是排队不是代码变慢（延迟换吞吐）。
- P95/avg ≈ 2；max 320/392ms 属 512m 堆 GC 与排队尾。

---

## 组 2：QPS 拐点（/api/stat/today，每档 20s）

| c | n | QPS | avg | P50 | P95 | P99 | max | fail |
|---|---|---|---|---|---|---|---|---|
| 50 | 72971 | 3645.0 | 13.7 | 11.7 | 30.4 | 44.7 | 117.8 | 0 |
| 100 | 62105 | 3098.7 | 31.9 | 27.0 | 71.7 | 109.5 | 309.7 | 0 |
| 150 | 59263 | 2951.9 | 49.5 | 39.9 | 116.4 | 178.7 | 620.0 | 0 |
| 200 | 61174 | 3047.3 | 61.3 | 49.0 | 143.2 | 222.4 | 1372.9 | 0 |
| 250 | 68229 | 3396.8 | 65.9 | 52.9 | 147.7 | 236.9 | 1692.5 | 0 |
| 300 | 63286 | 3145.6 | 79.5 | 59.0 | 195.2 | 324.5 | 2382.8 | 0 |

结论要点：
- 按「错误率 > 1% 或 P95 > 500ms」的停机规则：六档都没触发，最高 P95 195ms（c=300），fail 全 0。
- 按吞吐看：c=50 已达 3645 QPS，之后 QPS 在 2950–3400 之间平台，而 avg/P95 随并发线性上升 → 吞吐在约 50 并发饱和，之后只是排队。
- 瓶颈判断：容器 CPU 峰值 367%–473%（≈4.7 核 / 12 核）未打满机器；同时客户端与服务同机争抢 CPU，单机 Python 线程模型自身也是嫌疑。所以结论写「单实例吞吐平台约 3000–3600 QPS，随并发不再增长；瓶颈在排队与客户端自身，不是 SQL」。
- max 从 117ms 涨到 2.38s（c=300），长尾来自 512m 堆 GC。

---

## 组 3：缓存命中率（/api/wrong/hot，n=1000，c=1）

| 组 | n | avg | P50 | P95 | P99 | max | QPS | redis hits | misses | 缓存命中率 |
|---|---|---|---|---|---|---|---|---|---|---|
| 冷（DEL key 后） | 1000 | 1.0 | 0.8 | 1.2 | 3.3 | 25.5 | 1005.4 | 2006 | 2 | 99.80% |
| 热（key 在） | 1000 | 0.9 | 0.7 | 1.7 | 2.6 | 22.5 | 1056.4 | 2006 | 0 | 100% |

口径注记（必写进 benchmark）：
- redis hits 里含每次请求的 token 校验 GET（每次都命中），所以全口径命中率接近 100%，不能直接当缓存成效。
- 干净口径是 misses：miss 只可能来自缓存 key 本身。冷组 2 次（首次回源 + 回填竞态），热组 0 次。
- MySQL Com_select 差值不能用：实例存在约每 3 秒一次的背景 SELECT（空闲 3 秒也会 +1，与请求数无关），本次热组显示 3 即噪声。
- TTL 检查：EXISTS=1、TTL=1633s（30 分钟内，未过期）。

---

## 组 4：索引收益（影子表 question_idx_test，6400 行 = question 200 × 32）

分页 SQL：`SELECT id,subject,chapter,content FROM question_idx_test WHERE subject='Java' AND chapter='集合' AND difficulty=2 ORDER BY id LIMIT 10;`
计数 SQL：`SELECT COUNT(*) FROM question_idx_test WHERE subject='Java' AND chapter='集合' AND difficulty=2;`

| 状态 | 计数 type | 计数 rows | 计数实际扫描 | 计数实际耗时 | 分页 type/rows | 分页实际耗时 | Extra |
|---|---|---|---|---|---|---|---|
| A 无索引 | ALL | 6326 | 6400 行 | 0.97–1.28ms | index / 10（走 PRIMARY） | 0.038ms | Using where |
| B 两列 (subject,chapter) | ref | 1280 | 1280 行回表 | 2.11–2.17ms | ref / 1280 | 0.067ms | Using where |
| C 三列 (+difficulty) | ref | 448 | 448 行 | 0.10ms | ref / 448 | 0.041ms | Using index（覆盖索引） |

结论要点：
- 两列索引在 6400 行规模**比全表扫还慢**（1280 次回表 vs 6400 行顺序扫），因为选择性不足（1280/6400）。
- 三列索引才真正生效：rows 448、计数走覆盖索引、0.10ms（比全扫快约 12 倍，比两列快约 21 倍）。
- 分页查询在 A 状态走 PRIMARY 顺序扫（ORDER BY id LIMIT 10 只需扫 68 行），所以分页这一路三状态差别不大——差别在聚合/计数类查询。
- 这条「两列反而更慢」如实写，比编一个提升比例值钱。

---

## 组 5：事务正确性 —— 未做（需要 TransactionRollbackIT.java）

## 组 6：MQ 可靠性五场景 —— 未做（需要 bench_mq_reliability.sh）

## 验收第 10 条证据（已拿到）
```
size_t MaxHeapSize      = 536870912   {product} {command line}
size_t MaxMetaspaceSize = 201326592   {product} {environment}
```
（jcmd 不在 eclipse-temurin:21-jre 镜像里，改用 java -XX:+PrintFlagsFinal）
