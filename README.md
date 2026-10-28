# 智能学情诊断学习助手 (Study Helper)

> **一句话定位**：面向学生的刷题与错题管理后端系统。核心基于 Spring Boot 3.5 提供后端工程能力（事务、Redis 缓存、MQ 异步），AI 诊断报告作为其中一个业务模块。

**当前进度**：W2 已完成（题库与答题核心链路）。W1、W2 验收已全绿。

---

## 🏗️ 架构图

```text
[前端/客户端]
     │
     ▼
[Spring Boot 3.5 应用]
 ├── Interceptor / JWT 拦截鉴权
 ├── 统一返回体 R<T> / 全局异常处理
 ├── 业务层 (Controller / Service)
 │    ├── 用户模块 (注册/登录/登出)
 │    ├── 题库模块 (分页/筛选)
 │    ├── 答题模块 (判分/事务归集)
 │    ├── 错题本 (W3 待做)
 │    └── AI 学情报告 (W4/W5 待做)
 ├── 持久层 (MyBatis-Plus)
 └── 中间件交互
      ├── MySQL (主业务数据存储)
      ├── Redis (W1 存Token / W3 引入缓存)
      ├── RabbitMQ (W4 异步投递)
      └── Spring AI (W4 接入 DeepSeek)
```

---

## 🚀 快速启动 (Quick Start)

**前提条件**：本机已安装 JDK 21、Maven、Docker、Python。

**三步启动**：
1. **启动基础依赖 (MySQL / Redis)**
   ```bash
   cd deploy
   docker compose up -d
   ```
   *(注：MySQL 容器映射在宿主机 **3307** 端口，Redis 映射在 **6379** 端口)*

2. **导入基础题库数据 (200道题)**
   在项目根目录运行 Python 脚本（需先 `pip install pymysql`）：
   ```bash
   python scripts/generate_questions_csv.py
   python scripts/import_questions.py
   ```

3. **启动应用并访问文档**
   在 IDEA 中运行 `ProjectAApplication`。
   访问接口文档：[http://localhost:8080/doc.html](http://localhost:8080/doc.html)

---

## 🛠️ 技术栈

- **核心框架**：Spring Boot 3.5.5
- **运行环境**：JDK 21 (已开启虚拟线程配置)
- **数据库**：MySQL 8.0 (InnoDB，显式联合索引设计)
- **缓存**：Redis 7.2 (Spring Data Redis)
- **ORM**：MyBatis-Plus 3.5.17
- **鉴权**：JWT (jjwt 0.12.x) + 拦截器 + Redis 主动失效
- **接口文档**：Knife4j (OpenAPI 3)
- **工程规范**：统一返回体、全局异常、MDC TraceId 链路追踪

---

## 📡 接口列表与错误码

### 已实现接口 (W1 & W2)
| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/api/user/register` | 注册，密码 SHA-256 加密入库 (注1) |
| POST | `/api/user/login` | 登录，签发 JWT，写入 Redis |
| POST | `/api/user/logout` | 登出，删除 Redis Token 实现主动失效 |
| GET | `/api/user/info` | 获取当前用户信息 |
| PUT | `/api/user/info` | 修改个人信息 (如昵称) |
| GET | `/api/question/list` | 题目分页筛选 (不泄露答案/解析) |
| GET | `/api/question/{id}` | 题目详情 (不泄露答案/解析) |
| POST | `/api/answer/submit` | 提交答题，事务判分并归集错题 |

### 统一错误码 (部分)
| HTTP 状态码 | body 业务码 (code) | 说明 |
| :--- | :--- | :--- |
| 200 | 200 | 请求成功 |
| 400 | 400 | 参数校验失败 (MethodArgumentNotValidException) |
| 401 | 401 | 未登录或 Token 失效 (UNAUTHORIZED) |
| 404 | 404 | 资源不存在 (NOT_FOUND，如题目查询为空) |
| 500 | 500 | 系统内部异常 |
| - | 1001 | 用户名已存在 (USER_NAME_EXIST) |
| - | 1003 | 用户名或密码错误 (防账号枚举) |

---

## 💡 关键设计与排坑记录

更详细的技术取舍请查阅 `docs/decisions.md` 与 `docs/benchmark.md`（待建）。

1. **鉴权设计**：JWT 本身无法主动失效。登录时签发 Token 并同时写入 Redis；拦截器校验 JWT 验签通过后，**必须**去 Redis 比对，登出时只需删 Redis 即可踢下线。
2. **事务边界与失效**：答题判分与错题 upsert 包在 `@Transactional(rollbackFor = Exception.class)` 中。已亲手复现并记录三种事务失效场景（同类内部调用、非 public 方法、异常被吞）。
3. **并发幂等 upsert**：错题本收录依靠 `uk_user_question` 唯一索引 `ON DUPLICATE KEY UPDATE`，拒绝“先查后插”，防止并发漏洞。
4. **数据脱敏**：题库列表和详情接口使用专用的 `QuestionListVO` 和 `QuestionDetailVO`，防止 `answer` 和 `analysis` 字段意外泄露。
5. **开发妥协**：因前期依赖冲突，当前用户密码暂时使用手写 `SHA-256` 加盐替代 `BCrypt`（后续统一替换）。
6. **环境踩坑**：绝对不要在 PowerShell 中生成 Java 文件（会带 BOM 头导致运行报错）；MySQL 宿主机端口映射为 `3307`，连接时需注意。

---

## ⚠️ 已知未做项 (将在后续周次实现)
- **W3**：错题本列表查询、重练、手动移除；Redis 缓存高频错题 (Cache-Aside、防穿透/击穿/雪崩)。
- **W4**：RabbitMQ 接入；Spring AI 大模型调用与超时降级。
- **W5**：统计概览；SSE 流式输出（若时间不足可能砍掉）。
- **P1/P2**：批量提交试卷、管理员题目导入后台接口等。

---

## 🎬 演示脚本 (规划 7.3 版，W6 待录制)
1. `docker compose up -d` 一键起依赖。
2. `curl` 注册 + 登录。
3. 刷 3 道题（错 2 道），查库展示错题本自动出现。
4. 调报告生成接口，展示降级报告与状态。
```
