import requests
import pymysql
import subprocess
import datetime

BASE_URL = "http://localhost:8080"
USERNAME = "wrongtest1"
PASSWORD = "test12345"

# 数据库配置
db = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='root123', database='study_helper', autocommit=True)
cursor = db.cursor()

def sql_execute(sql, args=None):
    cursor.execute(sql, args)
    return cursor.fetchall()

def login():
    resp = requests.post(f"{BASE_URL}/api/user/login", json={"username": USERNAME, "password": PASSWORD})
    return resp.json()["data"]["token"]

def submit(token, qid, answer):
    headers = {"Authorization": f"Bearer {token}"}
    requests.post(f"{BASE_URL}/api/answer/submit", json={"questionId": qid, "userAnswer": answer}, headers=headers)

print("1. 开始登录...")
token = login()

print("2. 按科目各拉取 10 道题...")
headers = {"Authorization": f"Bearer {token}"}
subjects = ["Java", "MySQL", "Redis"]
questions_to_submit = []

for sub in subjects:
    # 拉取对应科目的题
    resp = requests.get(f"{BASE_URL}/api/question/list?subject={sub}&page=1&size=10", headers=headers)
    records = resp.json()["data"]["records"]
    questions_to_submit.extend(records)

print(f"✅ 获取到 {len(questions_to_submit)} 道题。")

print("3. 从数据库获取正确答案...")
right_answers = {}
for q in questions_to_submit:
    res = sql_execute("SELECT answer FROM question WHERE id = %s", (q["id"],))
    right_answers[q["id"]] = res[0][0]

print("4. 开始刷题（60% 故意答错）...")
for i, q in enumerate(questions_to_submit):
    qid = q["id"]
    correct = right_answers[qid]
    if i % 5 < 3:  # 60% 错
        wrong = next(c for c in "ABCD" if c != correct)
        submit(token, qid, wrong)
    else:
        submit(token, qid, correct)

print("5. 将前 18 条记录的时间分散到过去 6 天（仅本地造数）...")
today = datetime.date.today()

# 查出最近 18 条记录的 id，作为我们要修改的目标
recent_records = sql_execute("SELECT id FROM user_answer_record WHERE user_id = 2 ORDER BY id DESC LIMIT 18")
recent_ids = [r[0] for r in recent_records]

for d in range(1, 7):
    target_date = today - datetime.timedelta(days=d)

    # 从列表中每次取 3 个 ID 出来
    chunk_ids = recent_ids[(d-1)*3 : d*3]
    if not chunk_ids:
        break
    ids_str = ",".join(map(str, chunk_ids))

    # 精准更新这几个 id 的时间
    sql_execute(f"""
        UPDATE user_answer_record 
        SET create_time = DATE_SUB(NOW(), INTERVAL {d} DAY) + INTERVAL {d*3} MINUTE
        WHERE id IN ({ids_str});
    """)

    # 补上 Bitmap 打卡 (B2)
    # 拼接 stat:checkin:2:yyyyMM
    ym = target_date.strftime("%Y%m")
    day_offset = target_date.day - 1
    redis_key = f"stat:checkin:2:{ym}"
    subprocess.run(["docker", "exec", "study-redis", "redis-cli", "SETBIT", redis_key, str(day_offset), "1"], capture_output=True)
    # 补上打卡 Key 的过期时间 (96天)
    subprocess.run(["docker", "exec", "study-redis", "redis-cli", "EXPIRE", redis_key, "8294400"], capture_output=True)

print("6. 清理 Redis 今日统计缓存（B3，让对账脚本触发自愈）...")
today_str = today.strftime("%Y%m%d")
subprocess.run(["docker", "exec", "study-redis", "redis-cli", "DEL", f"stat:answer:2:{today_str}"], capture_output=True)

# 顺手调一下接口触发自动回填
requests.get(f"{BASE_URL}/api/stat/today", headers=headers)

print("7. 核对造数结果（过去 7 日每天的数量分布）...")
res = sql_execute("""
    SELECT DATE(create_time) AS day, COUNT(*) AS total 
    FROM user_answer_record WHERE user_id = 2 
    AND create_time >= CURDATE() - INTERVAL 7 DAY 
    GROUP BY DATE(create_time) ORDER BY day DESC;
""")
print("日期          答题数")
for row in res:
    print(f"{row[0]}    {row[1]:>4}")

print("🎉 造数完成！")