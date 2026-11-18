# W6 进度接续笔记

最后更新：2026-11-18 晚（W6 步骤 1–2 完成）

## 一句话现状

W6 第零节（W5 收尾复跑 18 项）已于 2026-11-16 完成；2026-11-18 完成部署基线（命名卷空库重建、镜像 tag 固定、演示造数脚本）。
下一步：**步骤 3 —— scripts/w6 下五个压测脚本** → 组 1–6 实测 → README/decisions/证据 → 演示视频 → tag v1.0。

## 已完成（均实测）

- 验收第 2 条：命名卷空库重建，question=200（Java 120 / MySQL 40 / Redis 40），ai_report=0，四容器 healthy
- 验收第 9 条材料：镜像 study-helper/app:v1.0，IMAGE ID 5019e47a631d；docker images 684MB / 磁盘 224MB；
  构成 = 基础镜像 eclipse-temurin:21-jre 462MB + app.jar 59.5MB；运行用户 app（uid=999）
- 验收第 10 条材料：JAVA_TOOL_OPTIONS=-Xmx512m -XX:MaxMetaspaceSize=192m（jcmd VM.flags / docker stats 待补）
- scripts/csv_to_sql.py → deploy/sql/06_seed_questions.sql（200 题，2 条 INSERT IGNORE）
- scripts/seed_demo.py：走接口造 6 题（错 4 对 2）。实测 todayTotal=6 todayCorrect=2 accuracy=0.3333 streak=1；
  Redis stat:answer:1:20261118 t=6 c=2；GETBIT stat:checkin:1:202611 17 = 1；user_answer_record 6 行 / wrong_book 4 行
- docs/evidence/w6-deploy-evidence.md 已写第 1–3 段
- 本地 main 与 origin/main、gitee/main 已同步

## 已定口径（不要重新讨论）

1. 数据目录用命名卷 study-mysql-data / study-redis-data / study-rabbitmq-data。
   绑定挂载时 `docker compose down -v` 删不掉目录，会假通过；旧 ./xxx-data 已移到 D:\Projects\_backup-w6\。
2. 镜像 tag 固定为 study-helper/app:v1.0。原先 app 服务没写 image:，tag 由目录名拼成 deploy-app:latest。
3. .dockerignore 必须放仓库根（构建 context 是 ..，deploy/.dockerignore 不生效）。
4. 8080 同一时刻只能有一个主人：写代码用 IDEA，压测与录视频用容器 study-app。
5. scripts/ 下一律用 `py -3.9`（D:\cxdownload\python.exe，有 pymysql/requests）；默认 python 3.14 没有。
6. 从聊天窗口复制长代码会被吃掉 `__` 和 `*`：用 Ctrl+Shift+V 纯文本粘贴，或分段给。
7. 组 5 事务测试用固定临时用户 test_tx_999999，不额外建测试库。
8. 压测口径：自研 Python 脚本（标准库 http.client），单机客户端，QPS 只代表单实例下限，不冒充 JMeter。

## 待办

1. deploy/.env 换有效 DEEPSEEK_API_KEY → `docker compose up -d app` → 重跑 seed_demo.py 拿到 **status=1** 的报告（演示第 5 步）；
   今晚那份 status=2 的保留，正好当演示第 7 步的降级对照。
2. 步骤 3：scripts/w6 五个脚本
   env_snapshot.sh / bench_http.py / bench_cache_hit.sh / bench_index.sh / bench_mq_reliability.sh
3. 组 1–6 实测（组 1 RT/P95、组 2 QPS 拐点、组 3 缓存命中率、组 4 索引收益、组 5 事务正确性、组 6 MQ 五场景）
   → docs/benchmark.md 的 W6 段、evidence 第 4–6 段、截图 6 张
4. M9 残留：Redis 宕机时 /stat/today 单次 8s → 失败后本地短路，目标 < 2s
5. README 六块重写、decisions D-42–D-49、3 分钟演示视频（8 步）、14 条验收逐条、tag v1.0 双远端

## 已知卡点

- 报告目前 status=2（DEEPSEEK key 无效或未填），演示第 5 步需要一份 status=1 的
- 宿主机 IDEA 进程与 study-app 抢 8080，压测前务必先停 IDE
- git 历史 6d0c677 之前有旧 DeepSeek key（已轮换失效）；彻底清需 git filter-repo，留到 W10
- gitee 走 HTTPS（每次 push 可能要求账号），origin 走 SSH（已验证可用）
