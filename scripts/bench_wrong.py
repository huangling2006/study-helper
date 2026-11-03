# -*- coding: utf-8 -*-
"""
W3 首份压测：冷/热两组打 GET /api/wrong/hot，统计 avg/P50/P95 与 fail 数。
用法：python scripts/bench_wrong.py <token> [并发数] [总请求数]
"""
import json
import statistics
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

# 根据 WrongBookController.java 的实际路径修改为 /api/wrong/hot
URL = "http://localhost:8080/api/wrong/hot"

def call(token):
    req = urllib.request.Request(URL, headers={"Authorization": "Bearer " + token})
    t0 = time.perf_counter()
    ok = True
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            resp.read()
    except Exception:
        ok = False
    return (time.perf_counter() - t0) * 1000, ok

def pct(sorted_vals, p):
    k = max(0, min(len(sorted_vals) - 1, int(round(p / 100 * (len(sorted_vals) - 1)))))
    return sorted_vals[k]

def run(token, concurrency, total, label):
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        results = list(pool.map(lambda _: call(token), range(total)))
    rts = sorted(r[0] for r in results)
    fails = sum(0 if r[1] else 1 for r in results)
    print(f"[{label}] n={total} c={concurrency} "
          f"avg={statistics.mean(rts):.1f}ms "
          f"P50={pct(rts, 50):.1f}ms "
          f"P95={pct(rts, 95):.1f}ms "
          f"max={rts[-1]:.1f}ms fail={fails}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python scripts/bench_wrong.py <token> [并发数] [总请求数]")
        sys.exit(1)

    token = sys.argv[1]
    # 默认 20 并发，总共打 200 次
    concurrency = int(sys.argv[2]) if len(sys.argv) > 2 else 20
    total = int(sys.argv[3]) if len(sys.argv) > 3 else 200

    print(f">>> 开始压测，目标 URL: {URL}")
    run(token, concurrency, total, "cold")
    run(token, concurrency, total, "hot")