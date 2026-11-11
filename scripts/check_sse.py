# scripts/check_sse.py
import sys
import urllib.request
import json

tok, req = sys.argv[1], sys.argv[2]

r = urllib.request.Request(
    f"http://localhost:8080/api/report/stream/{req}",
    headers={"Authorization": "Bearer " + tok, "Accept": "text/event-stream"}
)

events = []
with urllib.request.urlopen(r, timeout=20) as resp:
    assert resp.headers["Content-Type"].startswith("text/event-stream")
    # 按空行切分 SSE 帧
    for raw in resp.read().decode().split("\n\n"):
        lines = [l for l in raw.strip().splitlines() if l]
        if not lines:
            continue

        # 提取 event 名和 data 内容
        name = next((l[6:].strip() for l in lines if l.startswith("event:")), None)
        data = "".join(l[5:].strip() for l in lines if l.startswith("data:"))

        if name:
            events.append((name, data))

# 取出所有 event 的名字，例如 ['status', 'segment', 'segment'...]
names = [e[0] for e in events]

# 1. 基础顺序断言：必须由 status 开始，done 结束
assert names[0] == "status", f"起始事件必须是 status，实际：{names}"
assert names[-1] == "done", f"结束事件必须是 done，实际：{names}"

# 2. 数量断言：必须恰好 5 段 segment
assert names.count("segment") == 5, f"期望 5 个 segment，实际：{names}"

# 3. 字段顺序断言：提取 data 中的 type 校验
seg_types = []
for name, data in events:
    if name == "segment":
        try:
            # 解析 data 中的 JSON，取出 type 字段
            seg_types.append(json.loads(data)["type"])
        except Exception as ex:
            raise AssertionError(f"segment data 解析失败: {data}, 错误: {ex}")

expected_types = ["overall", "weakPoints", "causes", "suggestions", "practiceQuestionIds"]
assert seg_types == expected_types, f"segment 推送顺序错误，期望：{expected_types}，实际：{seg_types}"

print("✅ sse ok:", names)
print("✅ segment order:", seg_types)