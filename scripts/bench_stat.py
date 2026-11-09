# scripts/bench_stat.py
import time, sys, urllib.request, statistics
from concurrent.futures import ThreadPoolExecutor

BASE = "http://localhost:8080"
def call(token):
    req = urllib.request.Request(BASE + "/api/stat/today", headers={"Authorization": "Bearer " + token})
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=10) as resp:
        resp.read()
    return (time.perf_counter() - t0) * 1000

def main():
    token, c, total = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    with ThreadPoolExecutor(max_workers=c) as pool:
        rts = sorted(pool.map(lambda _: call(token), range(total)))
    p95 = rts[min(len(rts)-1, int(len(rts)*0.95))]
    print(f"Concurrency: {c}, Total: {total}, Avg: {statistics.mean(rts):.1f}ms, P95: {p95:.1f}ms")

if __name__ == "__main__":
    main()