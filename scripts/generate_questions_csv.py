# scripts/generate_questions_csv.py
import csv
import os

# 输出路径
OUT = "deploy/seed/questions.csv"
# 表头定义，必须和数据库字段对应
HEADER = [
    "id", "subject", "chapter", "content",
    "option_a", "option_b", "option_c", "option_d",
    "answer", "difficulty", "analysis"
]

# 定义5个科目/章节组合，每个生成40道，共200道
SUBJECT_CHAPTERS = [
    ("Java", "基础语法"),
    ("Java", "集合"),
    ("MySQL", "索引"),
    ("Redis", "缓存"),
    ("Java", "并发"),
]

# 确保文件夹存在
os.makedirs(os.path.dirname(OUT), exist_ok=True)

rows = []
qid = 1
for subject, chapter in SUBJECT_CHAPTERS:
    for i in range(1, 41):
        # 让答案在 ABCD 之间循环
        answer = "ABCD"[(qid - 1) % 4]
        difficulty = (i % 3) + 1
        rows.append({
            "id": qid,
            "subject": subject,
            "chapter": chapter,
            "content": f"{subject} {chapter} 第{i}题：请选择本题的标准答案（开发测试题）。",
            "option_a": f"{subject}-{chapter}-第{i}题-选项A",
            "option_b": f"{subject}-{chapter}-第{i}题-选项B",
            "option_c": f"{subject}-{chapter}-第{i}题-选项C",
            "option_d": f"{subject}-{chapter}-第{i}题-选项D",
            "answer": answer,
            "difficulty": difficulty,
            "analysis": f"标准答案：{answer}。本题用于 W2 导入、分页筛选与答题事务验证。",
        })
        qid += 1

# 写入 CSV 文件
with open(OUT, "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=HEADER)
    writer.writeheader()
    writer.writerows(rows)

print(f"成功生成 {len(rows)} 道题 -> {OUT}")