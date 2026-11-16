# 💊 智能学情诊断学习助手 (Study Helper)

> **一句话定位**：面向学生的刷题与错题管理后端系统。核心基于 Spring Boot 3.5 提供后端工程能力（事务、Redis 缓存三件套、MQ 异步与 AI 降级、统计与 SSE 分段推送），AI 诊断报告作为其中一个业务模块。

**当前进度**：W1–W5 功能闭环，统计口径已统一（错题重练与单题答题共用同一套 afterCommit 埋点，D-37）；4 个集成用例可复跑（`mvn test`，3 个事务失效实验 + 1 个统计口径回归）；容器化就绪，`docker compose up -d --build` 一条命令起 MySQL + Redis + RabbitMQ + app。W6 剩下的只有六组实测数字、演示视频与 v1.0 标签。

---

## 🏗️ 架构图

```text
[前端/客户端]
     │
     ▼
[Spring Boot 3.5 应用]
 ├── Interceptor / JWT 拦截鉴权 (Redis 宕机自动降级为纯 JWT 验签)
 ├── 统一返回体 R<T> / 全局异常处理 (含 400 参数越界) / TraceId 链路追踪
 ├── 业务层 (Controller / Service)
 │    ├── 用户模块 (注册/登录/登出)
 │    ├── 题库模块 (分页/筛选)
 │    ├── 答题模块 (判分/事务归集/Redis 统计)
 │    ├── 错题本模块 (分页/重练/高频 Top100)
 │    ├── 统计概览模块 (今日/科目/趋势/连续打卡)
 │    └── AI 学情报告 (MQ 异步 + Spring AI + 三层降级 + SSE 分段推送)
 ├── 持久层 (MyBatis-Plus)
 └── 中间件交互
      ├── MySQL (主业务数据存储)
      ├── Redis (Token 存储 / 缓存三件套 / 限流 / 每日 HASH 计数 / Bitmap 打卡 / SSE List)
      ├── RabbitMQ (报告异步投递 / TTL 重试队列 / DLQ 审计 / Confirm 回调降级)
      └── Spring AI (接入 DeepSeek)
🚀 快速启动 (Quick Start)
前提条件：Docker（含 compose 插件）；只在本地跑应用时需要 JDK 21 + Maven + Python。

方式一：一条命令起全套（推荐，容器里跑 app）

    cd deploy
    cp .env.example .env          # 首次：按需填 DEEPSEEK_API_KEY / JWT_SECRET，留空则报告走模板降级
    docker compose up -d --build
    curl http://localhost:8080/v3/api-docs     # 200 即就绪；文档 http://localhost:8080/doc.html

四服务与限额：MySQL 3307(容器 3306)、Redis 6379、RabbitMQ 5672/15672、app 8080；
compose 里已写健康检查与 mem_limit（D-40），app 会等三个中间件 health 后再启动。

题库数据（200 道）随 MySQL 初始化脚本自动导入；需要重新造数时：

    python scripts/generate_questions_csv.py
    python scripts/import_questions.py

方式二：中间件在容器、应用在 IDEA 里跑

    cd deploy && docker compose up -d mysql redis rabbitmq
    # IDEA 里给 ProjectAApplication 加环境变量 DEEPSEEK_API_KEY；DB_URL 默认已指 127.0.0.1:3307

跑测试（需要 MySQL 3307 + Redis 6379 已启动）：

    mvn test        # 3 个事务失效实验 + 1 个统计口径回归

🛠️ 技术栈
核心框架：Spring Boot 3.5.5

运行环境：JDK 21 (已开启虚拟线程配置)

数据库：MySQL 8.0 (InnoDB，显式联合索引设计)

缓存：Redis 7.2 (Spring Data Redis，包含 Lua 脚本)

消息队列：RabbitMQ 3.13 (含 DLX、TTL、死信队列)

ORM：MyBatis-Plus 3.5.17

AI 框架：Spring AI 1.1.8 (DeepSeek 模型)

鉴权：JWT (jjwt 0.12.x) + 拦截器 + Redis 主动失效 + BCrypt 密码加密

接口文档：Knife4j (OpenAPI 3)

工程规范：统一返回体、全局异常、MDC TraceId 链路追踪

📡 接口列表与错误码
已实现接口 (W1 ~ W5)

方法	路径	说明
POST	/api/user/register	注册，密码 BCrypt 加密入库
POST	/api/user/login	登录，签发 JWT，写入 Redis
POST	/api/user/logout	登出，删除 Redis Token 实现主动失效
GET	/api/user/info	获取当前用户信息
PUT	/api/user/info	修改个人信息 (如昵称)
GET	/api/question/list	题目分页筛选 (VO 脱敏，不泄露答案)
GET	/api/question/{id}	题目详情 (接入 Redis 缓存防穿透)
POST	/api/answer/submit	提交答题，事务判分并归集错题，afterCommit 写 Redis 统计
GET	/api/wrong/list	错题分页列表 (支持科目、错次、时间排序)
DELETE	/api/wrong/{id}	手动移除错题 (越权防护，返回 404)
GET	/api/wrong/practice	从错题本随机抽题重练
POST	/api/wrong/practice/submit	提交重练，答对移出错题本，答错计数+1
GET	/api/wrong/hot	高频错题 Top100 榜单 (Cache-Aside 缓存)
POST	/api/report/generate	触发 AI 学情报告 (MQ 异步，60s 限流，投递失败自动本地降级)
GET	/api/report/{requestId}	查询报告状态与结果 (包含三层降级兜底)
GET	/api/report/history	历史报告列表 (真分页，JSON 函数算摘要，不带大字段)
GET	/api/report/stream/{requestId}	SSE 分段推送报告 (基于 Redis List 实现，需 fetch 流式解析)
GET	/api/stat/today	今日统计与连续打卡 (Redis 优先，宕机降级查库)
GET	/api/stat/subject	科目正确率分布 (近 30 天，含 accuracy 字段)
GET	/api/stat/trend	近 N 日答题趋势 (缺失日期自动补零)
统一错误码 (部分)

HTTP 状态码	body 业务码 (code)	说明
200	200	请求成功
400	400	参数校验失败 (DTO 校验 / @Max 等约束越界拦截)
401	401	未登录或 Token 失效 (UNAUTHORIZED)
404	404	资源不存在或越权访问 (NOT_FOUND)
429	429	操作过于频繁 (触发限流)
500	500	系统内部异常
-	1001	用户名已存在 (USER_NAME_EXIST)
-	1003	用户名或密码错误 (防账号枚举)
💡 关键设计与排坑记录
更详细的技术取舍请查阅 docs/decisions.md 与 docs/benchmark.md。

密码安全升级：历史遗留密码使用无盐 SHA-256，W3 阶段完成了平滑迁移。登录时若匹配旧密文，自动重写为 BCrypt 密文，既兼容了老用户，又提升了安全性。

鉴权设计：JWT 本身无法主动失效。登录时签发 Token 并同时写入 Redis；拦截器校验 JWT 验签通过后，必须去 Redis 比对，登出时只需删 Redis 即可踢下线。

事务边界与失效：答题判分与错题 upsert 包在 @Transactional(rollbackFor = Exception.class) 中。已亲手复现并记录三种事务失效场景（同类内部调用、非 public 方法、异常被吞）。

并发幂等 upsert：错题本收录依靠 uk_user_question 唯一索引 ON DUPLICATE KEY UPDATE，拒绝“先查后插”，防止并发漏洞。

越权防护：所有错题本操作强制 SQL 带 user_id。删除他人的错题统一返回 404（不暴露资源存在性），而非 403。

Redis 缓存三件套：

防穿透：详情查不到时写入 "__NULL__" 哨兵，TTL 5 分钟。

防击穿：SET NX EX 抢锁 + 双检 + 自旋降级，解锁用 Lua 脚本防误删。

防雪崩：TTL 增加 ±20% 随机抖动。

缓存一致性（Cache-Aside）：写库后挂 afterCommit 删缓存；Top100 榜单额外加 500ms 延迟双删。

W4 异步与降级体系

MQ 异步：RabbitMQ 三层拓扑（主队列 + TTL 重试队列 + DLQ 审计）。

两层重试：调用级重试（AI 限流/超时，退避后直接降级并 ACK）与消息级重试（基建故障走 15s TTL 队列）。

真实聚合：废除了 W4 遗留的假数据，报告由 4 条真实 SQL（整体表现、薄弱点、趋势、重练ID）驱动。

并发防重：更新报告使用 update ... where status=0，防止重复消费改写。

投递失败本地降级：引入 CorrelationData 与 Confirm 回调，若投递 MQ 失败，直接删除限流 Key，并在本地生成 status=2 的模板报告，接口返回 200，避免僵尸数据。

traceId 跨线程：Web 线程的 traceId 透传进消息头，消费端放入 MDC。

W5 统计与流式推送

今日计数：采用 Redis HASH + Lua 脚本保证自增与过期时间原子性，TTL 严格定在次日 02:00，防止跨零点错乱。写入时机在答题事务的 afterCommit 中。

连续打卡：跨月读取上个月 Key 进行拼接，并按日循环判断

统计降级：Redis 宕机时，拦截器放宽 DataAccessException 并降级为纯 JWT 验签（保业务可用）；StatService 业务层降级直查 MySQL（保数据准确），返回 200。

SSE 分段推送：由于 BeanOutputConverter 需完整 JSON 解析，采用“落库后拆分为 5 段（overall/weakPoints/causes/suggestions/practiceQuestionIds）推入 Redis List，由 SseEmitter 每 800ms 轮询推送”的方案。前端使用 fetch + ReadableStream 携带 Authorization 头消费，避免 Token 拼入 URL。

⚠️ 已知未做项
P1/P2 扩展：批量提交试卷、管理员题目导入后台接口。

SSE 说明：未做 Token 级真流式（由于结构化 JSON 解析限制），目前为分段回放方案。

统计预聚合：暂未建表，当前单用户数据量极小，直查 MySQL 即可。触发条件为单用户 > 10w 条或 P95 > 200ms，届时再上预聚合表。

🎬 演示脚本 (W6 待录制)
docker compose up -d 一键起依赖。

curl 注册 + 登录。

刷 3 道题（错 2 道），查库展示错题本自动出现，查 Redis 查看今日统计 Hash 与打卡 Bitmap。

提交重练（答对一题），查库展示错题被移除。

调 /api/stat/today 和 /api/stat/subject，展示统计与连续打卡。

调 /api/report/history 展示历史分页；调 /api/report/stream/{requestId} 展示 SSE 分段推送（5段 + done）。

停掉 RabbitMQ（或改错 AI Key），触发报告，展示投递失败降级与 AI 失败降级（接口 200，status=2）。

停掉 Redis，调统计接口展示降级查 MySQL。