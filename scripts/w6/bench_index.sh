#!/usr/bin/env bash
# 组 4：索引收益（影子表 question_idx_test，6400 行，三状态对比）
# 用法：bash scripts/w6/bench_index.sh
# 业务表 question 一个字节都不动
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1

MYSQL_PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' deploy/.env 2>/dev/null | cut -d= -f2-)"
if [ -z "${MYSQL_PW}" ]; then
  echo "[ERR] deploy/.env 里没有 MYSQL_ROOT_PASSWORD"; exit 1
fi

Q="SELECT id, subject, chapter, content FROM question_idx_test
   WHERE subject='Java' AND chapter='集合' AND difficulty=2 ORDER BY id LIMIT 10;"

QC="SELECT COUNT(*) FROM question_idx_test
   WHERE subject='Java' AND chapter='集合' AND difficulty=2;"

sql() { docker exec -i study-mysql mysql -uroot -p"$MYSQL_PW" --default-character-set=utf8mb4 study_helper "$@"; }

echo "===== 组 4：索引收益（question_idx_test，影子表）====="
echo

echo "[1/4] 建影子表（结构同 question，去掉两个索引）"
sql 2>/dev/null <<'SQL'
DROP TABLE IF EXISTS question_idx_test;
CREATE TABLE question_idx_test LIKE question;
ALTER TABLE question_idx_test DROP INDEX idx_subject_chapter;
ALTER TABLE question_idx_test DROP INDEX idx_subject_chapter_difficulty;
SQL

echo "[2/4] 灌数据：question 200 行 × 32 轮 = 6400 行"
for k in $(seq 1 32); do
  sql 2>/dev/null <<SQL
INSERT INTO question_idx_test
  (id, subject, chapter, content, option_a, option_b, option_c, option_d,
   answer, difficulty, analysis, create_time)
SELECT id + $((1000 * k)), subject, chapter, content, option_a, option_b, option_c, option_d,
       answer, difficulty, analysis, create_time
FROM question;
SQL
done
ROWS=$(sql -N -e "SELECT COUNT(*) FROM question_idx_test;" 2>/dev/null)
echo "  实际行数 = $ROWS"
sql -N -e "ANALYZE TABLE question_idx_test;" >/dev/null 2>&1

echo
echo "[3/4] 三状态对比（两组查询：A 组是 09 文档口径的分页查询，B 组是 COUNT，用来暴露全扫成本）"
echo "分页 SQL: $Q"
echo "计数 SQL: $QC"
echo

state() {
  local name="$1"
  echo "--- 状态 $name ---"
  echo "  [分页] EXPLAIN:"
  sql -e "EXPLAIN $Q" 2>/dev/null
  echo "  [分页] EXPLAIN ANALYZE:"
  sql -r -e "EXPLAIN ANALYZE $Q" 2>/dev/null | tail -4
  echo "  [计数] EXPLAIN:"
  sql -e "EXPLAIN $QC" 2>/dev/null
  echo "  [计数] EXPLAIN ANALYZE:"
  sql -r -e "EXPLAIN ANALYZE $QC" 2>/dev/null | tail -3
  echo
}

state "A 无索引"
sql -e "ALTER TABLE question_idx_test ADD KEY idx_subject_chapter (subject, chapter);" 2>/dev/null
sql -N -e "ANALYZE TABLE question_idx_test;" >/dev/null 2>&1
state "B 两列 (subject, chapter)"

sql -e "ALTER TABLE question_idx_test ADD KEY idx_scd (subject, chapter, difficulty);" 2>/dev/null
sql -N -e "ANALYZE TABLE question_idx_test;" >/dev/null 2>&1
state "C 三列 (+ difficulty)"

echo "[4/4] 索引清单核对"
sql -N -e "SHOW INDEX FROM question_idx_test;" 2>/dev/null | awk '{print $3, $5}' | sort -u
echo
echo "收尾（不删也行，脚本下次会重建）："
echo "  docker exec study-mysql mysql -uroot -p<密码> study_helper -e 'DROP TABLE question_idx_test;'"
