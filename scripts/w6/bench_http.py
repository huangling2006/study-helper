#!/usr/bin/env python
"""W6 通用压测脚本，只用标准库，Python 3.9+

  py -3.9 scripts/w6/bench_http.py --url URL --token "$TOKEN" -c 100 -d 30 --label stat-today
  py -3.9 scripts/w6/bench_http.py --url URL --token "$TOKEN" -c 1 -n 1000 --label cache-cold
"""
import argparse
import http.client
import json
import sys
import threading
import time
from urllib.parse import urlparse

def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    k = int(round(p / 100.0 * (len(sorted_vals) - 1)))
    k = max(0, min(len(sorted_vals) - 1, k))
    return sorted_vals[k]

def worker(idx, args, deadline, counter, lock, out):
    u = urlparse(args.url)
    host = u.hostname
    port = u.port or 80
    path = u.path + (("?" + u.query) if u.query else "")
    headers = {}
    if args.token:
        headers["Authorization"] = "Bearer " + args.token
    if args.body:
        headers["Content-Type"] = "application/json"

    def new_conn():
        return http.client.HTTPConnection(host, port, timeout=args.timeout)

    conn = new_conn()
    for _ in range(args.warmup):
        try:
            conn.request(args.method, path, body=args.body, headers=headers)
            conn.getresponse().read()
        except Exception:
            try:
                conn.close()
            except Exception:
                pass
            conn = new_conn()

    lat = []
    fails = 0
    codes = {}
    while time.time() < deadline:
        if args.number:
            with lock:
                if counter[0] >= args.number:
                    break
                counter[0] += 1
        t0 = time.perf_counter()
        try:
            conn.request(args.method, path, body=args.body, headers=headers)
            resp = conn.getresponse()
            resp.read()
            code = resp.status
        except Exception:
            code = 0
            try:
                conn.close()
            except Exception:
                pass
            conn = new_conn()
        lat.append((time.perf_counter() - t0) * 1000.0)
        codes[code] = codes.get(code, 0) + 1
        if code == 0 or code >= 400:
            fails += 1
    try:
        conn.close()
    except Exception:
        pass
    out[idx] = (lat, fails, codes)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True)
    ap.add_argument("--token", default="")
    ap.add_argument("--method", default="GET")
    ap.add_argument("--body", default=None)
    ap.add_argument("-c", "--concurrency", type=int, default=1)
    ap.add_argument("-d", "--duration", type=float, default=0, help="秒，与 -n 二选一")
    ap.add_argument("-n", "--number", type=int, default=0, help="总请求数，与 -d 二选一")
    ap.add_argument("--label", default="bench")
    ap.add_argument("--timeout", type=float, default=10.0)
    ap.add_argument("--warmup", type=int, default=3)
    ap.add_argument("--out", default="", help="把 JSON 行追加到该文件")
    args = ap.parse_args()

    if args.duration <= 0 and args.number <= 0:
        sys.exit("必须给 -d 或 -n")

    counter = [0]
    lock = threading.Lock()
    out = [None] * args.concurrency
    start = time.time()
    deadline = start + args.duration if args.duration > 0 else start + 3600

    threads = [threading.Thread(target=worker,
                                args=(i, args, deadline, counter, lock, out))
               for i in range(args.concurrency)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = max(time.time() - start, 1e-6)

    all_lat = []
    fails = 0
    codes = {}
    for item in out:
        if not item:
            continue
        lat, f, c = item
        all_lat.extend(lat)
        fails += f
        for k, v in c.items():
            codes[k] = codes.get(k, 0) + v

    all_lat.sort()
    n = len(all_lat)
    if n == 0:
        sys.exit("没采到样本：检查 url / token / 服务是否在跑")
    avg = sum(all_lat) / n
    qps = n / elapsed
    code_str = ",".join("%s:%d" % (k, v) for k, v in sorted(codes.items()))

    print("[%s] n=%d c=%d dur=%.1fs avg=%.1fms p50=%.1f p95=%.1f p99=%.1f max=%.1f "
          "fail=%d qps=%.1f codes={%s}" % (
              args.label, n, args.concurrency, elapsed, avg,
              percentile(all_lat, 50), percentile(all_lat, 95),
              percentile(all_lat, 99), all_lat[-1], fails, qps, code_str))

    rec = {
        "label": args.label, "url": args.url, "method": args.method,
        "n": n, "c": args.concurrency, "duration_s": round(elapsed, 1),
        "avg_ms": round(avg, 1),
        "p50_ms": round(percentile(all_lat, 50), 1),
        "p95_ms": round(percentile(all_lat, 95), 1),
        "p99_ms": round(percentile(all_lat, 99), 1),
        "max_ms": round(all_lat[-1], 1),
        "fail": fails, "qps": round(qps, 1),
        "codes": {str(k): v for k, v in codes.items()},
        "at": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    print("JSON " + json.dumps(rec, ensure_ascii=False))
    if args.out:
        with open(args.out, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

if __name__ == "__main__":
    main()