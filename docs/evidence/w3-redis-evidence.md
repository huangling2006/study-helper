# W3 Redis 缓存三件套验证证据

## 1. 防穿透（空值哨兵）
- **操作**：请求 `GET /api/question/9999999`（查不存在的题目）
- **Redis 查询**：
    - `GET question:detail:9999999` -> 输出 `"__NULL__"`
    - `TTL question:detail:9999999` -> 输出 `(integer) 59`（5分钟以内）
- **结论**：空值成功写入缓存，防止后续恶意 ID 攻击打穿数据库。

## 2. 防雪崩（TTL 抖动） & 防击穿（分布式锁）
- **操作**：请求 `GET /api/wrong/hot` 写入缓存。
- **Redis 查询**：
    - `TTL wrong:hot:top100` -> 输出 `(integer) 1720`（基准1800±20%以内）
    - `GET wrong:hot:top100` -> 输出干净的 JSON 字符串，无 `@class`，包含 `generatedAt` 字段。
- **结论**：TTL 产生随机偏移，避免同一时刻集体失效；显式 JSON 序列化成功。

## 3. 写库删缓存（Cache-Aside 一致性）
- **操作**：先请求 `/hot` 预热缓存，再请求 `DELETE /api/wrong/8` 删除真实错题。
- **Redis 查询**：
    - `EXISTS wrong:hot:top100` -> 输出 `(integer) 0`
- **结论**：数据库变动后，缓存被成功删除，保证了缓存与数据库的最终一致性。