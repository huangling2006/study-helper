import csv
from pathlib import Path

src = Path("deploy/seed/questions.csv")
dst = Path("deploy/sql/06_seed_questions.sql")
cols = "id, subject, chapter, content, option_a, option_b, option_c, option_d, answer, difficulty, analysis"

rows = list(csv.DictReader(src.open(encoding="utf-8-sig")))
def q(v): return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"

out = ["SET NAMES utf8mb4;"]
for i in range(0, len(rows), 100):
    chunk = rows[i:i+100]
    values = ",\n".join(
        "(" + ",".join([r["id"]] + [q(r[c]) for c in
                                    ["subject","chapter","content","option_a","option_b","option_c","option_d","answer"]]
                       + [r["difficulty"], q(r["analysis"])]) + ")"
        for r in chunk)
    out.append(f"INSERT IGNORE INTO question ({cols}) VALUES\n{values};")
dst.write_text("\n\n".join(out) + "\n", encoding="utf-8")
print(f"wrote {dst} rows={len(rows)}")