import subprocess
import sys

# 配置你的环境信息
USER_ID = 2
TODAY = "20261109"

def run_cmd(cmd):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.stdout.strip()

def main():
    # 1. 查 MySQL 今日答题数
    mysql_sql = f"docker exec study-mysql mysql -uroot -proot123 study_helper -N -e \"SELECT COUNT(*) FROM user_answer_record WHERE user_id={USER_ID} AND DATE(create_time)=CURDATE();\""
    db_total = int(run_cmd(mysql_sql) or 0)

    # 2. 查 Redis 今日 t 字段
    redis_cmd = f"docker exec study-redis redis-cli HGET stat:answer:{USER_ID}:{TODAY} t"
    redis_total = int(run_cmd(redis_cmd) or 0)

    print(f"mysql={db_total} redis={redis_total} diff={db_total - redis_total}")

    if db_total != redis_total:
        print("对账失败！Redis 与 MySQL 不一致！")
        sys.exit(1)
    else:
        print("对账成功！diff=0")

if __name__ == "__main__":
    main()