# W6 进度接续笔记

最后更新：2026-11-20 晚（六组实测完成、文档重写完成）

## 一句话现状

W6 第零节（W5 收尾复跑 18 项）2026-11-16 完成；部署基线 2026-11-18 完成；**2026-11-20 晚完成六组实测、M9 收尾、README 六块、decisions D-42–D-50**。
剩下：14 条验收里的 3 / 4 / 7 / 9 / 11 / 12 逐条补证、截图 6 张、演示视频、tag v1.0、六个 commit。

## 已完成（均实测）

- 验收第 1、2 条：命名卷空库重建，question=200（Java 120 / MySQL 40 / Redis 40），ai_report=0，四容器 healthy
- 验收第 5、6 条：status=1 报告 `10a26650b38149f3815277643bf8829a`（deepseek-chat，tokenCost 841）；status=2 报告 `b2842a29b9704d61b139063ede4894d2`（template-v1，error_msg 401）
- 验收第 9 条：镜像 study-helper/app:v1.0，M9 重建后 IMAGE ID **9511e97bb973**，684MB / 磁盘 224MB，运行用户 app（uid=999）
- 验收第 10 条：`java -XX:+PrintFlagsFinal` → MaxHeapSize=536870912、MaxMetaspaceSize=201326592（jcmd 不在 JRE 镜像里，用不了）
- scripts/w6 五个脚本全部落盘并实跑：env_snapshot.sh / bench_http.py / bench_cache_hit.sh / bench_index.sh / bench_mq_reliability.sh
- 组 1 RT/P95、组 2 QPS 拐点、组 3 缓存命中率、组 4 索引收益、组 5 事务正确性、组 6 MQ 五场景 —— 数字见 docs/benchmark.md W6 段与 docs/evidence/w6-measurements-raw.md
- 组 5：新增 `TransactionRollbackIT`（正常提交 / 事务内异常回滚 / 调用方吞异常仍提交），`mvn test` 共 7 个用例全绿
- M9 收尾：`spring.data.redis.timeout=500ms` + StatService 30 秒本地短路；停 Redis 后 /api/stat/today 从 8.09s 降到 1.03s（首次）/ 0.52s（后续），接口始终 200
- README 按六块重写；docs/decisions.md 补 D-42–D-50；docs/demo-w6.md 八步演示脚本；.gitattributes 锁 LF

## 已定口径（不要重新讨论）

1. 数据目录用命名卷；**镜像重建后 tag 不变、IMAGE ID 会变**，证据里要写新 ID
2. 缓存命中率主口径 = `keyspace_misses`（miss 只来自缓存 key）；全口径 hits 只作对照；MySQL Com_select 有每 3 秒一次背景 SELECT，不能当回源数
3. 索引实验在影子表 `question_idx_test` 上做，**必须同时跑 COUNT 版本**，分页版本会走 PRIMARY，看不出差别
4. 组 6 S2 的标记表必须是 **MyISAM**：InnoDB 表在触发器里的写会随 SIGNAL 一起回滚，计数器永远减不动；次数取 4（3 次重试 + 第 4 次投递），第 5 次 fallback 才写成功
5. 组 6 S5 要**先停消费者**再投 300 条，否则消费比投递快，队列永远积压不起来
6. 组 6 每个场景换账号绕开 60 秒限流；S1 之后必须 `docker restart study-app`，否则 broker 恢复后连接不重建，后面全走本地降级
7. scripts/ 一律 `py -3.9`；压测期间不许重复登录（单端登录会踢掉之前的 token）

## 待办

1. 验收 3（doc.html 六分组）、4（新用户刷 3 题 → 错题 2 条 + HGETALL t=3 c=1 + 打卡位）、7（SSE 五段）、9（Config.User / id）、11（traceId 与 X-Trace-Id）、12（换目录验证）
2. 截图 6 张：四容器 healthy、镜像行、doc.html 分组、benchmark 段、SSE 五段、DLQ 队列
3. 3 分钟演示视频（docs/demo-w6.md，8 步，第 5 步 status=1、第 7 步 status=2）
4. 按六条粒度分 6 个 commit（脚本 / 事务测试 / M9 / benchmark+evidence / README+decisions / demo）
5. tag v1.0 双远端 + release notes

## 已知卡点

- 宿主机 IDEA 进程与 study-app 抢 8080，压测前先停 IDE
- git 历史 6d0c677 之前有旧 DeepSeek key（已轮换失效）；彻底清需 git filter-repo，留到 W10
- gitee 走 HTTPS（每次 push 可能要账号），origin 走 SSH
- 报告限流 60 秒：录制演示时第 5 步与第 7 步不要连在一起触发
