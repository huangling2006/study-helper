W6 部署与实测证据（项目 A v1.0）

环境：ThinkBook16 / i5-13500H / 15.5GB / Windows 11 / Docker 29.9.3（containerd 镜像存储）
仓库：D:\Projects\study-helper，W6 起始 HEAD a14339f
记录时间：2026-11-18

1. 一键启动

   $ docker compose config -q
   （无输出）

   $ docker compose up -d
   ✔ Volume study-mysql-data    Created
   ✔ Volume study-redis-data    Created
   ✔ Volume study-rabbitmq-data Created
   ✔ Container study-mysql      Healthy   16.6s
   ✔ Container study-redis      Healthy    5.5s
   ✔ Container study-rabbitmq   Healthy    6.4s
   ✔ Container study-app        Started

   $ docker ps --format "table {{.Names}}\t{{.Status}}"
   NAMES            STATUS
   study-app        Up 10 seconds (healthy)
   study-rabbitmq   Up 2 minutes (healthy)
   study-redis      Up 2 minutes (healthy)
   study-mysql      Up 2 minutes (healthy)

   应用启动日志：
   2026-11-18 22:33:07 [main] INFO com.hl.study.ProjectAApplication - []
   Started ProjectAApplication in 3.65 seconds (process running for 4.109)

首次 up -d 曾因宿主机 IDEA 进程占用 8080 失败一次（bind: Only one usage of each socket
address），停掉 IDE 进程后重跑成功。容器与 IDE 不能同时占用 8080，压测期间只跑容器。

2. 空库初始化

前置：docker compose down 后把 mysql-data / redis-data / rabbitmq-data 三个绑定挂载目录
移出仓库，再 up -d。命名卷为空时 initdb 才会执行；绑定挂载下 docker compose down -v
删不掉目录，会假通过（initdb 没重跑，看到的是旧表）。

    $ SHOW TABLES
    ai_report / question / user / user_answer_record / wrong_book

    $ SELECT COUNT(1) FROM question             → 200
    $ SELECT subject, COUNT(1) GROUP BY subject → Java 120 / MySQL 40 / Redis 40
    $ SELECT COUNT(1) FROM ai_report            → 0

initdb 执行顺序：01_schema → 02_question_answer_schema → 03_wrong_book_minimal
→ 04_wrong_book_index → 05_ai_report → 06_seed_questions

3. 镜像与非 root

   $ docker images | grep study-helper
   study-helper/app:v1.0   5019e47a631d   684MB   224MB

体积口径：684MB = containerd 解压后总和；224MB = 磁盘占用。
构成：基础镜像 eclipse-temurin:21-jre 462MB + 应用层 app.jar 59.5MB。
构建上下文：11.95kB（.dockerignore 在实际生效位置=仓库根，三个数据目录与 deploy/.env 已排除）。
运行用户：app（非 root，uid=999），JAVA_TOOL_OPTIONS=-Xmx512m -XX:MaxMetaspaceSize=192m
验收第 9、10 条（2026-11-20 实测）：

    $ docker inspect study-app --format '{{.Config.User}}'   → app
    $ docker exec study-app id                               → uid=999(app) gid=999(app) groups=999(app)
    $ docker exec study-app sh -c 'java -XX:+PrintFlagsFinal -version | grep -E " MaxHeapSize| MaxMetaspaceSize"'
       size_t MaxHeapSize      = 536870912   {product} {command line}
       size_t MaxMetaspaceSize = 201326592   {product} {environment}
    $ docker stats --no-stream study-app                     → MEM 483–511MiB / 768MiB

注：JRE 镜像里没有 jcmd（只有 java / keytool / jfr 等），验收里写的 `jcmd 1 VM.flags` 执行不了，
改用 `java -XX:+PrintFlagsFinal` 读同一份命令行参数，结论等价。

M9 收尾后应用镜像重建过：tag 仍是 study-helper/app:v1.0，IMAGE ID 从 5019e47a631d 变为 **3efd6a1e4698**。

4. 六组实测数字（2026-11-20）

   完整表格与结论在 docs/benchmark.md 的 W6 段；这里只留每组的关键原始行。

   组 1 RT/P95（c=100 d=30s，单机客户端）
     [q-list]    n=95819  avg=31.1ms p50=27.6 p95=64.2 p99=95.1 max=320.6 fail=0 qps=3190.3
     [wrong-hot] n=134061 avg=22.2ms p50=18.7 p95=51.2 p99=74.2 max=195.0 fail=0 qps=4463.1
     [stat-today] n=92847 avg=32.2ms p50=27.9 p95=69.4 p99=100.5 max=392.6 fail=0 qps=3091.5

   组 2 QPS 拐点（六档，每档 20s）
     c=50  qps=3645.0 p95=30.4   ← 吞吐平台从这里开始
     c=100 qps=3098.7 p95=71.7
     c=150 qps=2951.9 p95=116.4
     c=200 qps=3047.3 p95=143.2
     c=250 qps=3396.8 p95=147.7
     c=300 qps=3145.6 p95=195.2
     期间容器 CPU 367%–473%（≈4.7 核 / 12 核），未打满 → 瓶颈是排队与单机客户端，不是算力

   组 3 缓存（/api/wrong/hot，n=1000 c=1，misses 口径）
     [cold] misses=2 命中率 99.80%  avg=1.0ms max=25.5ms
     [hot]  misses=0 命中率 100%    avg=0.9ms max=22.5ms
     TTL=1633s（未过期）；MySQL Com_select 有每 3 秒一次背景 SELECT，不能当回源数

   组 4 索引（question_idx_test 6400 行，COUNT 查询）
     A 无索引  type=ALL rows=6326 实际扫 6400 行 1.28ms
     B 两列    type=ref rows=1280 回表 1280 行        2.17ms  ← 比全扫还慢
     C 三列    type=ref rows=448  Using index（覆盖）  0.10ms

   组 5 事务（mvn -Dtest=TransactionRollbackIT test）
     Tests run: 3, Failures: 0, Errors: 0
     正常提交：user_answer_record +1、wrong_book +1、ai_report 不变
     事务内异常：三表 count 完全一致
     调用方吞异常：记录仍在（事务边界在 submit 方法上）
     全仓 mvn test：Tests run: 7, Failures: 0（3 事务失效 + 3 原子性 + 1 统计口径）

   组 6 MQ 五场景（bash scripts/w6/bench_mq_reliability.sh）
     S1 停 broker 生成    status=2 template-v1 publish_failed，HTTP 200，无 status=0 僵尸
     S2 连续消费失败      retry=1/2/3/4（间隔 17s）→ 第 4 次写模板 status=2 → DLQ messages=1
     S3 消费中 kill 应用  队列 messages=1（未 ACK 回队列）→ 重启后重消费 → status=1，该 requestId 仅 1 行
     S4 重复投递          1/841 → 1/841 不变，幂等拦截日志 1 条
     S5 队列深度          停消费者投 300 条 → 积压 282 → 4 秒清空（≈70 条/秒）

5. 演示链路（W6 验收 3 / 4 / 7，2026-11-20 实测）

   验收 3：doc.html 六分组
     $ curl -s localhost:8080/v3/api-docs | py -3.9 -c "..."
       ['answer', 'question', 'report', 'stat', 'user', 'wrong']

   验收 4：新用户刷 3 题（错 2）→ 错题归集 + Redis 计数 + 打卡位
     q=1 我选A 正确A -> 对
     q=2 我选A 正确B -> 错   wrongAdded=True
     q=3 我选A 正确C -> 错   wrongAdded=True
     错题本 total=2
     redis HGETALL stat:answer:34:20261120 → t 3  c 1
     GETBIT stat:checkin:34:202611 19     → 1
     /api/stat/today → todayTotal=2 … （注入前 3 题那次返回 t=3 c=1 如上一行）

   验收 5：status=1 报告
     requestId=10a26650b38149f3815277643bf8829a  model=deepseek-chat tokenCost=841

   验收 7：SSE 五段
     $ py -3.9 scripts/check_sse.py "$TOKEN" 852348972cd6441397844dba9ed7cce8
       sse ok: ['status','segment','segment','segment','segment','segment','done']
       segment order: ['overall','weakPoints','causes','suggestions','practiceQuestionIds']
       Content-Type: text/event-stream

   验收 6：status=2 降级对照
     requestId=b2842a29b9704d61b139063ede4894d2  model=template-v1  error_msg 以 401 Authentication Fails 开头

   验收 11：traceId 与日志同值（4xx 现场）
     $ curl -si -X POST localhost:8080/api/report/generate -H "Authorization: Bearer ***"
       HTTP/1.1 429  traceId: daa9c2a5011d4f4b
     $ docker logs study-app | grep daa9c2a5011d4f4b
       2026-11-20 22:30:16 [tomcat-handler-76] WARN ...GlobalExceptionHandler - [daa9c2a5011d4f4b] business error, code=429, msg=操作过于频繁

6. 换目录验证（验收第 12 条，2026-11-20）

   把仓库整份复制到 D:\tmp\study-helper-clone（去掉 .git，只改 deploy/.env），原目录 compose down 后在新目录 up -d：

     $ cd /d/tmp/study-helper-clone/deploy && docker compose up -d
     $ docker ps
       study-app        Up 45 seconds (healthy)
       study-rabbitmq   Up 51 seconds (healthy)
       study-mysql      Up 51 seconds (healthy)
       study-redis      Up 51 seconds (healthy)

   演示第 1–4 步在新目录里全部走通：
     注册 → data=35；登录 → token（166 字符）
     答题 questionId=10 → isCorrect=false，correctAnswer=B，wrongBookAdded=true
     /api/wrong/list → total=1
     /api/stat/today → todayTotal=1 todayCorrect=0 streak=1

   数据沿用命名卷（study-mysql-data 等三个卷名写死在 compose 里），所以换目录不等于空库；
   要空库重建必须 `docker compose down -v`，这一条已写进 README 排障。
   验证完成后关闭克隆栈、在原目录重新 up -d，恢复演示现场。
