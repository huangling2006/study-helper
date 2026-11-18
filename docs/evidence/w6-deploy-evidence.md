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
待验（验收第 9、10 条）：

    docker inspect study-app --format '{{.Config.User}}'   → 期望 app
    docker exec study-app id                               → 期望 uid=999(app)
    docker exec study-app jcmd 1 VM.flags | grep -i maxheapsize
    docker stats --no-stream study-app
