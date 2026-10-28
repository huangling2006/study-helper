# scripts/import_questions.py
import csv
import os
import sys
import pymysql

CSV_PATH = "deploy/seed/questions.csv"
EXPECTED_HEADER = [
    "id", "subject", "chapter", "content",
    "option_a", "option_b", "option_c", "option_d",
    "answer", "difficulty", "analysis"
]

# 数据库配置，端口是 3307
DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DB_PORT = int(os.getenv("DB_PORT", "3307"))
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "root123")
DB_NAME = os.getenv("DB_NAME", "study_helper")

def fail(msg):
    print(f"[FAIL] {msg}", file=sys.stderr)
    sys.exit(1)

# 1. 读取并校验 CSV
with open(CSV_PATH, "r", encoding="utf-8", newline="") as f:
    reader = csv.DictReader(f)
    if reader.fieldnames != EXPECTED_HEADER:
        fail(f"表头不匹配！")

    rows = []
    for line_no, row in enumerate(reader, start=2):
        # 简单校验必填项
        for col in EXPECTED_HEADER:
            if not row[col] or not row[col].strip():
                fail(f"第 {line_no} 行字段 {col} 为空")

        answer = row["answer"].strip().upper()
        difficulty = int(row["difficulty"])
        qid = int(row["id"])

        rows.append((
            qid, row["subject"].strip(), row["chapter"].strip(), row["content"].strip(),
            row["option_a"].strip(), row["option_b"].strip(), row["option_c"].strip(), row["option_d"].strip(),
            answer, difficulty, row["analysis"].strip(),
        ))

# 2. 连接数据库导入
print(f"准备导入 {len(rows)} 条数据...")
try:
    conn = pymysql.connect(
        host=DB_HOST, port=DB_PORT, user=DB_USER, password=DB_PASSWORD,
        database=DB_NAME, charset="utf8mb4", autocommit=False
    )
except Exception as e:
    fail(f"数据库连接失败，请检查 Docker 是否启动: {e}")

# 使用 ON DUPLICATE KEY UPDATE 保证重复导入不报错
sql = """
INSERT INTO question
(id, subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis)
VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
ON DUPLICATE KEY UPDATE
 subject=VALUES(subject), chapter=VALUES(chapter), content=VALUES(content),
 option_a=VALUES(option_a), option_b=VALUES(option_b), option_c=VALUES(option_c), option_d=VALUES(option_d),
 answer=VALUES(answer), difficulty=VALUES(difficulty), analysis=VALUES(analysis)
"""

try:
    with conn.cursor() as cur:
        for r in rows:
            cur.execute(sql, r)
    conn.commit()
    print(f"✅ 成功导入 {len(rows)} 条数据！")
finally:
    conn.close()