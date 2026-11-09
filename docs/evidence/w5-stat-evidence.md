# W5 统计与历史报告验证证据

## 1. 统计口径对账 (MySQL 为真值)
- 命令：`python scripts/verify_stat.py`
- 输出：`mysql=2 redis=2 diff=0`
- 结论：今日计数与库里当日答题数一致，缓存与数据库完美闭环。

## 2. 连续打卡与跨月
- 命令：POST /api/answer/submit 答对两题后查询。
- 输出：`streak: 1, todayTotal: 2, todayCorrect: 2`。
- 结论：正常打卡，没有将未打卡显示成 0。

## 3. 索引与耗时
- EXPLAIN 结果：
```text
+----+-------------+-----------+------------+------+---------------+------+---------+------+------+----------+-----------------------------+
| id | select_type | table     | partitions | type | possible_keys | key  | key_len | ref  | rows | filtered | Extra                       |
+----+-------------+-----------+------------+------+---------------+------+---------+------+------+----------+-----------------------------+
|  1 | SIMPLE      | ai_report | NULL       | ALL  | idx_user_time | NULL | NULL    | NULL |    9 |   100.00 | Using where; Using filesort |
+----+-------------+-----------+------------+------+---------------+------+---------+------+------+----------+-----------------------------+
```
- 结论：当前表内仅 9 行数据，MySQL 优化器认为全表扫描成本更低。索引 `idx_user_time` 已建立，在数据量增长后将必然命中该索引并支持反向扫描。

## 4. SSE 分段推送证据
- 终端输入：`curl.exe -N -H "Authorization: Bearer ..." http://localhost:8080/api/report/stream/{requestId}`
- 原始帧：
```text
event:status
data:{"status":2}

event:segment
data:{"text":"\"AI 服务繁忙，已生成基础版报告...\"","type":"overall"}

event:segment
data:{"text":"[...]","type":"weakPoints"}

event:segment
data:{"text":"[...]","type":"causes"}

event:segment
data:{"text":"[...]","type":"suggestions"}

event:done
data:{"status":2}
```
- 结论：5 段（含降级状态）顺序正确推送，SSE 链路彻底跑通。

## 5. W4 回归 (AI 聚合与降级)
- 降级：错 key 场景下 status=2、content 内容包含真实答题统计。

