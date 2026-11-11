# 💊 智能学情诊断学习助手 (Study Helper)

> **一句话定位**：面向学生的刷题与错题管理后端系统。核心基于 Spring Boot 3.5 提供后端工程能力（事务、Redis 缓存三件套、MQ 异步与 AI 降级、统计与 SSE 分段推送），AI 诊断报告作为其中一个业务模块。

**当前进度**：W5 功能完美闭环（历史报告真分页、SSE 5段对齐、统计口径校验与三路降级）；准备进入 W6 容器化部署与实测。

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
前提条件：本机已安装 JDK 21、Maven、Docker、Python。

三步启动：

启动基础依赖 (MySQL / Redis / RabbitMQ)

bash
cd deploy
docker compose up -d
(注：MySQL 容器映射在宿主机 3307 端口，Redis 映射在 6379 端口，RabbitMQ 映射在 5672/15672)

导入基础题库数据 (200道题)
在项目根目录运行 Python 脚本（需先 pip install pymysql）：

bash
python scripts/generate_questions_csv.py
python scripts/import_questions.py
配置 AI 密钥并启动应用
在 IDEA 的 Edit Configurations -> Environment variables 中填入：
DEEPSEEK_API_KEY=sk-你的真实Key
(注：启动前必须配置，不配会启动失败，属预期。历史版本中曾泄露的旧 key 已轮换)
在 IDEA 中运行 ProjectAApplication。
访问接口文档：http://localhost:8080/doc.html

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

连续打卡：使用 Redis Bitmap 存储，BITFIELD GET u31 一次读取整月，基于 MSB 位序处理跨月连续天数。

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