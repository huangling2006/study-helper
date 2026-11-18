import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import quote

import pymysql

BASE = os.environ.get("APP_BASE", "http://localhost:8080")
DB = dict(
    host="127.0.0.1",
    port=int(os.environ.get("DB_PORT", "3307")),
    user="root",
    password=os.environ.get("DB_PASSWORD", "root123"),
    database="study_helper",
    charset="utf8mb4",
)
SUBJECTS = ["Java", "MySQL", "Redis"]
PER_SUBJECT = 2          # 每个科目取 2 题，一共 6 题
CORRECT_COUNT = 2        # 故意只答对 2 题，错 4 题


def call(method, path, token=None, body=None, timeout=20):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "ignore")
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"raw": raw}


def need(step, status, js):
    if status >= 400:
        print("[FAIL] %s: HTTP %s %s" % (step, status, json.dumps(js, ensure_ascii=False)[:400]))
        sys.exit(1)


def register_or_login(username, password):
    st, js = call("POST", "/api/user/register",
                  body={"username": username, "password": password})
    if st >= 400:
        print("注册返回 HTTP %s（账号已存在属正常），转登录：%s" % (st, js.get("message")))
    st, js = call("POST", "/api/user/login",
                  body={"username": username, "password": password})
    need("登录", st, js)
    data = js["data"]
    print("登录成功 userId=%s username=%s" % (data["userId"], data["username"]))
    return data["token"], data["userId"]


def pick_ids(subject, token):
    st, js = call("GET", "/api/question/list?page=1&size=%d&subject=%s"
                  % (PER_SUBJECT * 2, quote(subject)), token=token)
    need("取题[%s]" % subject, st, js)
    recs = js["data"].get("records") or []
    ids = [(r.get("id") or r.get("questionId")) for r in recs][:PER_SUBJECT]
    if len(ids) < PER_SUBJECT:
        print("[FAIL] 科目 %s 题量不足：拿到 %d 道" % (subject, len(ids)))
        sys.exit(1)
    return ids


def read_answers(ids):
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            marks = ",".join(["%s"] * len(ids))
            cur.execute("SELECT id, answer FROM question WHERE id IN (%s)" % marks, ids)
            return {row[0]: row[1].strip().upper() for row in cur.fetchall()}
    finally:
        conn.close()


def wrong_letter(answer):
    for c in "ABCD":
        if c != answer:
            return c
    return "A"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--user", default="demo")
    ap.add_argument("--password", default="demo12345")
    ap.add_argument("--force-answer", action="store_true",
                    help="当天已答过题也再答一轮（会产生重复记录，只在演示前排障时用）")
    args = ap.parse_args()

    token, uid = register_or_login(args.user, args.password)

    # ---- 幂等：今天已经答过就不重复答题 ----
    st, js = call("GET", "/api/stat/today", token=token)
    need("今日统计", st, js)
    today_total = js["data"].get("todayTotal", 0)
    print("答题前 todayTotal=%s" % today_total)

    plan = []
    if today_total >= len(SUBJECTS) * PER_SUBJECT and not args.force_answer:
        print("今日已有答题记录，跳过答题阶段（--force-answer 可强制重答）")
    else:
        for subject in SUBJECTS:
            plan.extend(pick_ids(subject, token))
        answers = read_answers(plan)
        print("本题计划：%d 题 %s" % (len(plan), plan))

        correct_ids = set(plan[:CORRECT_COUNT])   # 前 2 题答对，其余故意答错
        for qid in plan:
            real = answers[qid]
            mine = real if qid in correct_ids else wrong_letter(real)
            st, js = call("POST", "/api/answer/submit", token=token,
                          body={"questionId": qid, "userAnswer": mine})
            need("提交第 %s 题" % qid, st, js)
            d = js["data"]
            print("  q=%-4s 我选 %s 正确 %s -> %s  归集=%s"
                  % (qid, mine, d["correctAnswer"],
                     "对" if d["isCorrect"] else "错", d["wrongBookAdded"]))
            time.sleep(0.2)

        st, js = call("GET", "/api/stat/today", token=token)
        need("答题后统计", st, js)
        print("答题后 today=%s" % json.dumps(js["data"], ensure_ascii=False))

    # ---- 生成报告：60 秒限流命中就复用最近一份 ----
    st, js = call("POST", "/api/report/generate", token=token)
    if st == 429:
        print("命中 60 秒限流，复用最近一份报告")
        st2, js2 = call("GET", "/api/report/history?page=1&size=1", token=token)
        need("报告历史", st2, js2)
        recs = js2["data"].get("records") or []
        if not recs:
            print("[FAIL] 既被限流又没有历史报告，等 60 秒后重跑本脚本")
            sys.exit(1)
        rid = recs[0]["requestId"]
    else:
        need("生成报告", st, js)
        rid = js["data"]["requestId"]
    print("requestId=%s" % rid)

    status = None
    for i in range(20):
        st, js = call("GET", "/api/report/" + rid, token=token)
        need("轮询报告", st, js)
        d = js["data"]
        status = d["status"]
        if status in (1, 2):
            print("轮询 %d 次后 status=%s aiSuccess=%s tokenCost=%s"
                  % (i + 1, status, d.get("aiSuccess"), d.get("tokenCost")))
            print("createTime=%s finishTime=%s" % (d.get("createTime"), d.get("finishTime")))
            try:
                content = json.loads(d["content"] or "{}")
                wp = content.get("weakPoints") or []
                print("weakPoints 前两项：%s" % json.dumps(wp[:2], ensure_ascii=False))
                print("overall 前 80 字：%s" % (content.get("overall", "")[:80]))
            except (ValueError, KeyError):
                print("content 不是合法 JSON（降级模板也可能如此），原文前 120 字：%s"
                      % (d.get("content") or "")[:120])
            break
        time.sleep(2)
    else:
        print("[WARN] 轮询 20 次仍未到终态，status=%s，去查 docker logs study-app" % status)

    print("\n===== 演示现场就绪 =====")
    print("USER_ID=%s" % uid)
    print("TOKEN=%s" % token)
    print("REQUEST_ID=%s" % rid)


if __name__ == "__main__":
    main()
