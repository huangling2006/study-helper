#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
W5 造数脚本：
1. 走接口真实刷 40 道题（保留判分与错题归集链路）。
2. 通过 UPDATE 将其中 18 条记录的时间分散到过去 6 天（仅限本地造数，严禁用于生产）。
"""
import json
import subprocess
import urllib.request
import random

BASE = "http://localhost:8080"

def login(username="wrongtest1", password="test12345"):
    req = urllib.request.Request(
        BASE + "/api/user/login",
        data=json.dumps({"username": username, "password": password}).encode(),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)["data"]["token"]

def get(path, tok):
    req = urllib.request.Request(BASE + path, headers={"Authorization": "Bearer " + tok})
    with urllib.request.urlopen(req) as r:
        return json.load(r)

def post(path, tok, body):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + tok
        }
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)

def mysql(sql):
    """在 Docker 容器中执行 MySQL 命令"""
    result = subprocess.run(
        ["docker", "exec", "study-mysql", "mysql", "-uroot", "-proot123", "study_helper", "-N", "-e", sql],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print("SQL Error:", result.stderr)
        return ""
    return result.stdout.strip()

if __name__ == "__main__":
    print("1. 开始登录获取 Token...")
    try:
        tok = login()
    except Exception as e:
        print("登录失败，请确认应用已启动且 wrongtest1 账号存在。", e)
        exit(1)

    print("2. 拉取 40 道题目...")
    qs = get("/api/question/list?page=1&size=40", tok)["data"]["records"]

    print("3. 从数据库获取正确答案（因为接口不返回 answer）...")
    right_answers = {}
    rows = mysql("SELECT id, answer FROM question LIMIT 40").splitlines()
    for row in rows:
        if "\t" in row:
            qid, ans = row.split("\t")
            right_answers[qid] = ans

    print("4. 开始刷题（60% 概率故意答错）...")
    success_count = 0
    for i, q in enumerate(qs):
        qid = str(q["id"])
        correct = right_answers.get(qid, "A")

        if random.random() < 0.6: # 60% 答错
            submit = next(c for c in "ABCD" if c != correct)
        else:
            submit = correct

        try:
            post("/api/answer/submit", tok, {"questionId": q["id"], "userAnswer": submit})
            success_count += 1
        except Exception as e:
            print(f"题目 {qid} 提交失败: {e}")

    print(f"✅ 成功刷了 {success_count} 道题。")

    print("5. 将前 18 条记录的时间分散到过去 6 天（仅本地造数）...")
    # 先查出最新写入的 18 条记录的主键 ID
    ids = mysql("SELECT id FROM user_answer_record WHERE user_id = 2 ORDER BY id DESC LIMIT 18;").splitlines()

    # 按顺序分配到过去 6 天，每天分配 3 条
    for i, record_id in enumerate(ids):
        d = (i // 3) + 1  # 比如前 3 条是 1 天前，接下来 3 条是 2 天前...
        sql = f"""
        UPDATE user_answer_record 
        SET create_time = DATE_SUB(NOW(), INTERVAL {d} DAY) + INTERVAL {d*3} MINUTE 
        WHERE id = {record_id};
        """
        mysql(sql)

    print("6. 核对造数结果（过去 7 日每天的数量分布）...")
    res = mysql("SELECT DATE(create_time) AS d, COUNT(*) FROM user_answer_record WHERE user_id=2 GROUP BY DATE(create_time) ORDER BY d DESC;")
    print("日期\t\t答题数")
    for line in res.splitlines():
        print(line.replace("\t", "\t\t"))

    print("\n🎉 造数完成！你可以去调用 /api/stat/trend?days=7 查看真实数据了。")