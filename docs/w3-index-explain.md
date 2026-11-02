1.索引全部命中：EXPLAIN 结果显示，列表查询走了 idx_user_time（甚至出现了 Backward index scan，说明倒序完美利用了索引）；Top100 聚合走了 idx_question。

2.覆盖索引（Using index）：Top100 那条查询的 Extra 里出现了 Using index，意思是只查了索引树，连回表都没回。这是极高的性能表现，面试官问“怎么优化统计查询”时，这个细节非常加分