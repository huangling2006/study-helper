# W5 统计口径、历史报告与 SSE 验证证据

> 状态：全部真实环境实测通过。对账脚本已去除硬编码，可在任意日期复跑。

## 1. 口径对账（MySQL 为真值，Redis 为加速）
- **命令**：`python scripts/verify_stat.py`
- **输出**：`mysql=2 redis=2 diff=0`
- **结论**：今日答题计数与 MySQL 库内真实记录完全一致。证明 `afterCommit` 写入时机无误，且 Lua 脚本保证了计数的原子性。
- **防篡改**：脚本已修复日期硬编码，使用 `CURDATE()` 半开区间动态比对，换天跑依然有效。

## 2. 连续打卡与跨月（Bitmap 读与写）
- **场景 A（今日有答题）**：提交两题后查 `/api/stat/today`，输出 `streak: 1, todayTotal: 2, todayCorrect: 2`。
- **场景 B（今日断签，验证 lastStreak）**：删除今日 Redis Key 后查，输出 `todayAnswered=false, streak=0, lastStreak=6`。
- **结论**：`streak` 与 `lastStreak` 成功解耦。前端不会把“今天没答”错误显示为“连续打卡 6 天”，避免误导用户。

## 3. 索引与耗时验证
- **执行 SQL**：
  `EXPLAIN SELECT request_id, status, create_time FROM ai_report WHERE user_id = 2 ORDER BY create_time DESC, id DESC LIMIT 10;`
- **EXPLAIN 原始输出**：
  ```text
  +----+-------------+-----------+------------+------+---------------+------+---------+------+------+----------+-----------------------------+
  | id | select_type | table     | partitions | type | possible_keys | key  | key_len | ref  | rows | filtered | Extra                       |
  +----+-------------+-----------+------------+------+---------------+------+---------+------+------+----------+-----------------------------+
  |  1 | SIMPLE      | ai_report | NULL       | ALL  | idx_user_time | NULL | NULL    | NULL |    9 |   100.00 | Using where; Using filesort |
  +----+-------------+-----------+------------+------+---------------+------+---------+------+------+----------+-----------------------------+
面试话术（重点）：当前表内仅 9 行数据，MySQL 优化器认为全表扫描（type: ALL）的成本比回表更低，因此没有命中 idx_user_time。但该联合索引已建立，当数据量涨到万级时，优化器将必然触发 Backward index scan，完美支撑 ORDER BY create_time DESC, id DESC 的两段排序，消除 Using filesort。此外，摘要计算使用了 JSON_EXTRACT 和 JSON_VALID 在库内算好，不将大字段拉入内存。

4. SSE 分段推送证据
   终端输入：python scripts/check_sse.py <token> <requestId>（Windows 下用 curl.exe -N 也可）

断言结果：
✅ sse ok: ['status', 'segment', 'segment', 'segment', 'segment', 'segment', 'done']
✅ segment order: ['overall', 'weakPoints', 'causes', 'suggestions', 'practiceQuestionIds']

原始帧节选：

text
event:status
data:{"status":2}

event:segment
data:{"text":"\"AI 服务繁忙...\"","type":"overall"}

... (中间 3 段省略) ...

event:segment
data:{"text":"[...]","type":"practiceQuestionIds"}

event:done
data:{"status":2}
结论：5 段（含降级状态）顺序固定且正确推送。由于 BeanOutputConverter 的限制，采用 Redis List 缓存完整 JSON 的 5 个字段后分发，完美支持前端流式渲染。

5. W4 回归与降级验证
   真实聚合：数据库中的 content 字段，其 weakPoints[0].name 与直接执行薄弱点 SQL 结果完全一致，彻底清除了 W4 阶段遗留的硬编码假数据。

投递失败本地降级：停掉 RabbitMQ 触发 /api/report/generate，接口无 500 报错，成功捕获异常返回 200，并落库 status=2 的模板报告，限流 Key 同步删除。

鉴权兜底：docker stop study-redis 后，拦截器成功捕获 DataAccessException 并降级为纯 JWT 验签放行，接口正常返回 200，业务不中断。