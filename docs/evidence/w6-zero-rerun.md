# W6 第零节复跑证据（2026-11-16）

> 本文件是桌面《11-W6第零节复跑核对（2026-11-16）.docx》的同源留档。
> 仓库改动前的 HEAD = c13b03c；本次改动见下（本地 commit，未推送远端）。

## 1. 本次修掉的两类问题

### 1.1 统计埋点漏链路（W5 遗留 B3 的真根因）

- 现象（修复前实测）：调一次 `/api/wrong/practice/submit` 答错 1 题后
  - `/api/stat/today` → `todayTotal=0`、`streak=0`
  - `/api/stat/trend?days=1` → `total=1`（读 MySQL，正确）
  - `python scripts/verify_stat.py` → `mysql=1 redis=0 diff=1`，退出码 1
  - 打卡位 `GETBIT stat:checkin:2:202611 <今日-1>` = 0
- 根因：`incrQuietly` / `markQuietly` 只挂在 `AnswerService.submit` 的 afterCommit；
  `WrongBookService.submitPractice` 写了 `user_answer_record` 却没有 Redis 埋点。
  另外 `/stat/today` 未命中时会把错误的 0 回填进 Redis（`backfillQuietly`），到次日 02:00 前一直读这个 0。
- 修复：
  - `submitPractice` 在事务 afterCommit 里逐题 `dailyStatCounter.incrQuietly(userId, isCorrect)` + `checkinBitmap.markQuietly(...)` 一次
  - 口径定为「重练也算今日答题」（decisions D-37）
  - `correct` 改为答对数、`skipped` 作为子集，`total = correct + wrong`，`accuracy = correct/total`（D-38）
- 修复后实测：`todayTotal=1`、`streak=8`、`verify_stat.py` → `mysql=1 redis=1 diff=0`、打卡位 = 1

### 1.2 测试与证据不可复跑（W2 的第三实验 + 上下文起不来）

- 修复前：`mvn test` → `Tests run: 2, Errors: 2`，`Caused by: NoSuchBeanDefinitionException: SpringDocConfigProperties`
  （`WebEnvironment.NONE` 下 springdoc 的 bean 不存在，knife4j 自动配置直接失败）
- 修复：测试改 `RANDOM_PORT` + `spring.rabbitmq.listener.simple.auto-startup=false` + 占位 api-key；
  补第三种事务失效（private 方法上的 `@Transactional`，CGLIB 无法覆盖 private）
- 修复后：`mvn -o -B test` → `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS

## 2. 18 项验收逐条复跑（全部通过）

| # | 检查项 | 实测 |
|---|---|---|
| 1 | 登录 wrongtest1 | HTTP 200 |
| 2 | history?page=1&size=10 | 200，10 条，total=15，倒序 |
| 3 | history?page=1&size=20 | 200，15 条，total=15 |
| 4 | history size=21 | 400 `size:最大不能超过20` |
| 5 | trend days=100 | 400 `trend.days:最大不能超过30` |
| 6 | 无 token /stat/today | 401 |
| 7 | /stat/subject | 200，Java 195 / MySQL 20 / Redis 20，sum=235 = 近 30 天 MySQL |
| 8 | /stat/today（重练前） | todayTotal=0，streak=0，lastStreak=7 |
| 9 | trend days=7 | 7 点连续 11-10…11-16 |
| 10 | 重练埋点 Redis +1 | t 0→1，返回 total=1 correct=0 wrong=1 |
| 11 | /stat/today 同步 | todayTotal=1 = Redis t=1 |
| 12 | 打卡位 | GETBIT = 1 |
| 13 | verify_stat.py | mysql=1 redis=1 diff=0，exit=0 |
| 14 | /report/generate | 200，status=0 |
| 15 | 同用户连点 | 429 |
| 16 | AI 失败降级 | status=2、tokenCost=0、content 带真实数字 |
| 17 | SSE | status + 5 段 + done，段顺序正确 |
| 18 | 越权/不存在报告 | 404 |

## 3. 容器化验证

- `docker compose config --quiet` 通过
- 四容器 healthy：mysql / redis / rabbitmq / app
- app 容器以 `uid=999(app)` 非 root 运行；`JAVA_TOOL_OPTIONS=-Xmx512m -XX:MaxMetaspaceSize=192m`
- 限额（docker inspect HostConfig.Memory）：app 768MB、mysql 768MB、redis 256MB、rabbitmq 512MB
- redis 生效参数：`maxmemory=209715200`、`maxmemory-policy=allkeys-lru`
- rabbitmq 生效参数：`vm_memory_high_watermark=0.4`（走 deploy/rabbitmq.conf）
- 容器内 `/v3/api-docs`、`/doc.html` 均 200；登录 + 历史列表 + /stat/today 正常

### 3.1 只有真跑才暴露的三个坑

1. RabbitMQ 3.13 起 `RABBITMQ_VM_MEMORY_HIGH_WATERMARK` 环境变量被判定 deprecated，设置了直接启动失败 → 改用 `deploy/rabbitmq.conf`。
2. redis 启动参数写成 YAML 列表（`- --appendonly=yes`）会被 entrypoint 当成配置文件解析，报 `Bad directive or wrong number of arguments` → 改回字符串形式。
3. 健康检查里 `/dev/tcp` 只在 bash 存在，`/bin/sh`(dash) 报 `cannot create /dev/tcp/...`，容器永远 healthy=starting → 显式 `bash -c`。

## 4. 仍未完成（W6 主体）

- 六组压测数字（RT/P95、QPS 拐点、缓存命中率、索引收益、事务正确性、MQ 可靠性）——benchmark.md 的 W5 段组 1 至今写着「未测」。
- 3 分钟演示视频、双远端 tag v1.0。
- Redis 宕机时统计接口单次 RT 8s（Lettuce 2s 超时叠加多次调用）需要「失败后本地短路」。
- git 历史里的旧 DeepSeek key（6d0c677 之前）已轮换；彻底清理需 git filter-repo。
