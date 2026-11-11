import subprocess, sys

def mysql(sql):
    out = subprocess.run(["docker", "exec", "study-mysql", "mysql", "-uroot", "-proot123",
                          "study_helper", "-N", "-e", sql], capture_output=True, text=True)
    return out.stdout.strip()

def redis(*args):
    return subprocess.run(["docker", "exec", "study-redis", "redis-cli", *args],
                          capture_output=True, text=True).stdout.strip()

today = mysql("SELECT DATE_FORMAT(NOW(),'%Y%m%d')").strip()
key = f"stat:answer:2:{today}"

db_total = int(mysql("SELECT COUNT(*) FROM user_answer_record WHERE user_id=2 "
                     "AND create_time >= CURDATE() AND create_time < CURDATE() + INTERVAL 1 DAY").strip() or 0)
r_total = int(redis("HGET", key, "t") or 0)

print(f"mysql={db_total} redis={r_total} diff={db_total - r_total}")
print("checkin bit:", redis("GETBIT", "stat:checkin:2:" + today[:6], str(int(today[6:]) - 1)))

sys.exit(0 if db_total == r_total else 1)