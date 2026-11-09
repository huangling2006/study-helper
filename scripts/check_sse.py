# scripts/check_sse.py
import sys, urllib.request
tok, req = sys.argv[1], sys.argv[2]
r = urllib.request.Request(f"http://localhost:8080/api/report/stream/{req}",
                           headers={"Authorization": "Bearer " + tok, "Accept": "text/event-stream"})
events = []
with urllib.request.urlopen(r, timeout=20) as resp:
    assert resp.headers["Content-Type"].startswith("text/event-stream")
    for raw in resp.read().decode().split("\n\n"):
        lines = [l for l in raw.strip().splitlines() if l]
        if not lines: continue
        name = next((l[6:].strip() for l in lines if l.startswith("event:")), None)
        events.append(name)
names = [e for e in events if e]
assert names[0] == "status", names
assert names[-1] == "done", names
assert names.count("segment") >= 4, names
print("sse ok:", names)