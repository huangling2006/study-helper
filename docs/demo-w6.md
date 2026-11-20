# W6 演示脚本（8 步，3 分钟内）

录制前检查：
- 四容器 healthy：`docker ps --format '{{.Names}}\t{{.Status}}'`
- 题库 200：`docker exec study-mysql mysql -uroot -p<密码> -N study_helper -e "SELECT COUNT(*) FROM question"`
- demo 账号可登录（**录制前重新登一次**，本项目单端登录，后登的会踢掉前面的）
- `.env` 里的 DEEPSEEK_API_KEY 有效（否则第 5 步会掉到 status=2）
- 终端字号 ≥ 16pt、分辨率 1920×1080、关闭通知

固定现场（已经跑出来的两条，不用重新生成）：

| 用途 | requestId | status | 说明 |
|---|---|---|---|
| 第 5 步：AI 正常报告 | `10a26650b38149f3815277643bf8829a` | 1 | deepseek-chat，tokenCost=841，weakPoints 与 SQL 一致 |
| 第 7 步：降级对照 | `b2842a29b9704d61b139063ede4894d2` | 2 | template-v1，error_msg 里可见 401（key 无效那次） |

---

## 第 1 步（25s）一条命令起全套

```bash
cd /d/Projects/study-helper/deploy
docker compose up -d
docker ps --format '{{.Names}}\t{{.Status}}'
```

台词：四个服务带健康检查起来，app 会等 MySQL / Redis / RabbitMQ 都 healthy 之后才启动，不会出现「应用先起、连不上库再重启」的循环。

## 第 2 步（15s）注册 + 登录

```bash
cd /d/Projects/study-helper
TOKEN=$(curl -s -X POST localhost:8080/api/user/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo12345"}' \
  | py -3.9 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
echo ${#TOKEN}
```

台词：token 既签成 JWT 返回给前端，也写进 Redis——单端登录靠这个实现，后登录的会覆盖前面的。

## 第 3 步（30s）刷 3 题、错题自动归集

```bash
curl -s -X POST localhost:8080/api/answer/submit -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"questionId":1,"userAnswer":"A"}'
curl -s "localhost:8080/api/wrong/list?page=1&size=10" -H "Authorization: Bearer $TOKEN"
```

台词：判分、写答题记录、错题 upsert 在同一个事务里；错题靠唯一索引 ON DUPLICATE KEY UPDATE，不是先查后插。

## 第 4 步（20s）统计与 Redis 现场

```bash
curl -s localhost:8080/api/stat/today -H "Authorization: Bearer $TOKEN"
docker exec study-redis redis-cli HGETALL stat:answer:1:20261120
docker exec study-redis redis-cli GETBIT stat:checkin:1:202611 19
```

台词：计数在事务 afterCommit 里写，回滚就不会脏；打卡用 Bitmap，一次 BITFIELD 读整月。

## 第 5 步（30s）生成报告，轮到 status=1

```bash
curl -s -X POST localhost:8080/api/report/generate -H "Authorization: Bearer $TOKEN"
# 等 2 秒后查
curl -s localhost:8080/api/report/10a26650b38149f3815277643bf8829a -H "Authorization: Bearer $TOKEN"
```

台词：接口立刻返回 requestId（status=0），消息进 MQ，消费者聚合 4 条 SQL 后调 DeepSeek，条件更新成 status=1；weakPoints 里的数字和直接查库算出来的薄弱点 Top5 一致。

## 第 6 步（25s）SSE 五段回放

```bash
py -3.9 scripts/check_sse.py "$TOKEN" 10a26650b38149f3815277643bf8829a
```

台词：这不是 token 级真流式，是报告落库后拆成 5 段推 Redis List 再分段回放——结构化 JSON 必须完整解析才能用，这一点我主动说。

## 第 7 步（30s）降级对照：同一接口，status=2

```bash
curl -s localhost:8080/api/report/b2842a29b9704d61b139063ede4894d2 -H "Authorization: Bearer $TOKEN"
```

台词：key 失效那次，消费端三层降级到模板报告，接口仍然 200，overall 里的答题数和正确率是真的——因为模板和 AI 用的是同一条聚合 SQL，error_msg 里能看到 401。

## 第 8 步（15s）接口文档收尾

浏览器打开 `http://localhost:8080/doc.html`，展示 6 个分组。

台词：六组接口、统一返回体、错误码表都在这里。

---

## 录制后

- 视频放 `docs/demo/`（或 B 站不公开链接写进 README），文件名 `demo-w6-a-v1.0.mp4`
- README 第 6 块补一行可点入口
