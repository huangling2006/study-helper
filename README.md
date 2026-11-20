# 💊 智能学情诊断学习助手 (Study Helper)

> **一句话定位**：面向学生的刷题与错题管理后端，重点不在业务 CRUD，而在把一条真实链路做扎实——答题事务与错题归集、Redis 缓存三件套、MQ 异步 + AI 三层降级、统计口径与对账、一键容器化与六组实测。

**当前状态**：项目 A v1.0（W1–W6）。本地 `mvn test` 7 个用例全绿（3 个事务失效实验 + 3 个事务原子性断言 + 1 个统计口径回归）；`docker compose up -d` 一条命令起 MySQL / Redis / RabbitMQ / 应用四服务，空卷自动建表并灌 200 题；六组实测数字见 `docs/benchmark.md` 的 W6 段。

---

## 1. 架构与数据流

```text
                       ┌────────────────┐
  客户端 / curl ──────▶ │ Spring Boot 3.5 │ 8080
                       └───────┬────────┘
        ┌──────────────┬───────┴────────┬────────────────┐
        ▼              ▼                ▼                ▼
   MySQL 8.0       Redis 7.2        RabbitMQ 3.13    DeepSeek API
  业务主数据       token 校验        报告异步投递       AI 诊断报告
  答题记录/错题    Top100 缓存        TTL 重试队列       （失败降级模板）
  报告/统计聚合    每日 HASH 计数      DLQ 审计
                   打卡 Bitmap
                   SSE 分段 List
```

四条链路各自的可观测点：

- **答题**：`POST /api/answer/submit` → 事务内写 `user_answer_record` + 错题 upsert → `afterCommit` 写 Redis 日计数与打卡位。
- **报告**：`POST /api/report/generate` → 落 `ai_report(status=0)` → 投 MQ → 消费者聚合 4 条 SQL → 调 DeepSeek → 条件更新 `status=1`；失败按三层降级落到 `status=2`。
- **统计**：`/api/stat/*` 读 Redis（HASH / Bitmap），Redis 不可用时降级查 MySQL 并回填。
- **流式**：报告落库后拆 5 段推 Redis List，`/api/report/stream/{requestId}` 分段回放。

## 2. 启动（三步）

```bash
git clone <repo> && cd study-helper/deploy
cp .env.example .env          # 填 DEEPSEEK_API_KEY；不填也能跑，报告走模板降级 status=2
docker compose up -d          # 首次会自动构建镜像，四个服务按健康检查顺序起来
```

就绪判断：`curl http://localhost:8080/v3/api-docs` 返回 200，接口文档 `http://localhost:8080/doc.html`。

- 端口：app 8080、MySQL 3307（容器 3306）、Redis 6379、RabbitMQ 5672 / 控制台 15672。
- 空卷时 initdb 自动建 5 张表并灌 200 题（Java 120 / MySQL 40 / Redis 40）；改了种子 SQL 必须 `docker compose down -v` 再起。
- 宿主机跑应用（IDEA）时：`docker compose up -d mysql redis rabbitmq`，DB 默认已指 `127.0.0.1:3307`。
- 跑测试：`mvn test`（需 MySQL 3307 + Redis 6379 已启动）；脚本一律用 `py -3.9`（本机 3.14 没有 pymysql）。

## 3. 接口与错误码

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 用户 | POST | /api/user/register | 注册，BCrypt，用户名唯一索引防并发重名 |
| 用户 | POST | /api/user/login | 登录，签发 JWT 并写 Redis（单端登录） |
| 用户 | POST | /api/user/logout | 删 Redis token 即踢下线 |
| 题库 | GET | /api/question/list | 分页筛选，VO 脱敏不带 answer |
| 题库 | GET | /api/question/{id} | 详情，Cache-Aside + 空值缓存 |
| 答题 | POST | /api/answer/submit | 事务判分 + 错题归集 + afterCommit 埋点 |
| 错题 | GET | /api/wrong/list | 分页，SQL 必带 user_id |
| 错题 | DELETE | /api/wrong/{id} | 越权统一 404 |
| 错题 | GET | /api/wrong/practice | 随机抽题重练 |
| 错题 | POST | /api/wrong/practice/submit | 重练提交，与单题共用统计口径（D-37） |
| 错题 | GET | /api/wrong/hot | Top100 榜单（Redis 缓存） |
| 报告 | POST | /api/report/generate | 触发异步生成，60 秒限流 |
| 报告 | GET | /api/report/{requestId} | 查状态与内容 |
| 报告 | GET | /api/report/history | 历史列表，真分页 |
| 报告 | GET | /api/report/stream/{requestId} | SSE 五段回放 |
| 统计 | GET | /api/stat/today | 今日概览 + 连续打卡 |
| 统计 | GET | /api/stat/subject | 近 30 天科目分布 |
| 统计 | GET | /api/stat/trend?days=N | 近 N 日趋势，缺失补零 |

HTTP 语义码（`body.status` 与之一致）与业务码分开：

| HTTP | body.code | 场景 |
|---|---|---|
| 200 | 200 | 成功 |
| 400 | 400 | 参数校验失败（含 size>20 / days>30 越界） |
| 401 | 401 | 未登录或 token 失效 |
| 404 | 404 | 资源不存在或越权（不暴露存在性） |
| 429 | 429 | 生成报告触发 60 秒限流 |
| 500 | 500 | 未预期异常 |
| 200 | 1001 | 用户名已存在 |
| 200 | 1003 | 用户名或密码错误（防账号枚举） |

## 4. 关键设计（详见 docs/decisions.md）

**事务与幂等**：判分、写记录、错题 upsert 同属一个 `@Transactional` 边界，异常即整体回滚；错题归集靠 `uk_user_question` 唯一索引 `ON DUPLICATE KEY UPDATE`，不做先查后插。三种事务失效（同类内部调用 / 非 public / 异常被吞）由测试钉住，事务原子性由 `TransactionRollbackIT` 三个方法断言（D-47）。

**缓存三件套**：穿透写 `__NULL__` 哨兵、击穿用 `SET NX EX` 抢锁双检、雪崩给 TTL ±20% 抖动；写库后 `afterCommit` 删缓存，Top100 额外延迟双删。命中率口径用 `keyspace_misses` 而不是累计 hits——token 校验每次请求都 GET 一次 Redis，全口径命中率必然虚高（D-46）。

**异步与降级**：报告投递走主队列 + 15 秒 TTL 重试队列 + DLQ 审计；消费异常最多重试 3 次，之后写模板报告 `status=2` 并投审计消息进 DLQ。AI 侧三层降级（重试 → 模板 → 兜底），任何情况下接口不返回 5xx；投递失败用 Confirm 回调本地降级，不留 status=0 僵尸数据（D-45）。

**统计口径**：日计数用 HASH + Lua 保证自增与过期原子性，TTL 定在次日 02:00；打卡用 Bitmap 一次 `BITFIELD` 读整月。单题答题与错题重练共用同一套 `afterCommit` 埋点（D-37），Redis 故障时降级查 MySQL 并回填，同时在 30 秒窗口内本地短路，避免每条请求都等一次超时（D-50、D-31）。

## 5. 已知未做项

- 批量提交试卷、管理端题目导入后台：不做（P1/P2）。
- SSE 是**分段回放**（报告落库后拆 5 段推 Redis List），不是 token 级真流式——结构化 JSON 需要完整解析，这一点主动说明。
- 统计未建预聚合表：触发条件是单用户 >10 万条答题记录或统计接口 P95 >200ms。
- 无 Nginx / HTTPS / CI / Prometheus：单实例直连足够演示，可观测性用 actuator + traceId 讲（D-49）。
- 限流是固定 60 秒窗口，边界会放过双倍流量；令牌桶留给项目 B。
- 历史提交里仍有旧的明文 DeepSeek key（已轮换失效），彻底清理需 `git filter-repo`，留到收尾阶段。

## 6. 演示与实测摘要

八步演示脚本（完整版与每步期望输出见 `docs/demo-w6.md`）：

```bash
# 1 一条命令起全套
cd deploy && docker compose up -d && docker ps          # 四行 healthy

# 2 注册 + 登录
curl -s -X POST localhost:8080/api/user/register -H 'Content-Type: application/json' -d '{"username":"demo","password":"demo12345"}'
TOKEN=$(curl -s -X POST localhost:8080/api/user/login -H 'Content-Type: application/json' -d '{"username":"demo","password":"demo12345"}' | py -3.9 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 3 刷题（错题自动归集）
curl -s -X POST localhost:8080/api/answer/submit -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"questionId":1,"userAnswer":"A"}'
curl -s "localhost:8080/api/wrong/list?page=1&size=10" -H "Authorization: Bearer $TOKEN"

# 4 统计与 Redis 现场
curl -s localhost:8080/api/stat/today -H "Authorization: Bearer $TOKEN"
docker exec study-redis redis-cli HGETALL stat:answer:1:20261120

# 5 生成报告（status=1，AI 真跑）
RID=$(curl -s -X POST localhost:8080/api/report/generate -H "Authorization: Bearer $TOKEN" | py -3.9 -c "import sys,json;print(json.load(sys.stdin)['data']['requestId'])")

# 6 SSE 五段回放
py -3.9 scripts/check_sse.py "$TOKEN" "$RID"

# 7 降级对照（错 key 或停 MQ → 200 + status=2，固定 requestId 见 docs/demo-w6.md）
curl -s localhost:8080/api/report/<降级那份 requestId> -H "Authorization: Bearer $TOKEN"

# 8 接口文档
# 浏览器打开 http://localhost:8080/doc.html
```

实测摘要（完整数据与原始输出见 `docs/benchmark.md` W6 段）：

| 项目 | 数字 | 测试条件 |
|---|---|---|
| 接口 P95 | question/list 64.2ms；wrong/hot 51.2ms；stat/today 69.4ms | c=100、30 秒、单实例、-Xmx512m、单机客户端 |
| 吞吐 | c=50 时 3645 QPS，之后稳定在 2950–3400 | /api/stat/today，六档梯度 50→300 |
| 缓存 | 冷组命中 99.80%（misses=2/1000）、热组 100% | c=1、n=1000，`CONFIG RESETSTAT` + misses 口径 |
| 索引 | 6400 行：无索引全扫 1.28ms、两列 2.17ms、三列覆盖索引 0.10ms | 影子表 question_idx_test，COUNT 查询 |
| 事务 | 正常提交三表 +1/+1/0；事务内异常三表一行不动 | `mvn -Dtest=TransactionRollbackIT test` |
| MQ | 停 broker 仍 200 + status=2；重试 3 次后进 DLQ 并落模板报告 | 五场景见 benchmark |

> 数字口径：压测用自研 Python 脚本（标准库 http.client，复用连接），不是 JMeter；客户端与服务同机，QPS 只能代表单实例下限。这一句 benchmark 里也写着。
