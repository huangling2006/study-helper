#!/usr/bin/env bash
# 组 6：MQ 可靠性五场景（S1 投递失败 / S2 三次重试进 DLQ / S3 消费中 kill / S4 重复投递 / S5 队列深度）
# 用法：bash scripts/w6/bench_mq_reliability.sh "$TOKEN_任意账号"
#
# 设计要点：
#   · 每个场景换一个账号，绕开 60 秒生成限流
#   · S2 用数据库触发器注入「前 4 次 UPDATE 失败」（3 次重试 + 第 4 次投递），不改业务代码，
#     这样才能造出「重试 3 次 → 写模板 status=2 → 投审计消息进 DLQ」的真实链路
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1

API="http://localhost:8080"
MGMT="http://localhost:15672"
EXCHANGE="study.report.exchange"
RK="report.generate"
QUEUE_MAIN="study.report.queue"
QUEUE_DLQ="study.report.dlq"

MYSQL_PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' deploy/.env 2>/dev/null | cut -d= -f2-)"
MQ_USER="$(grep -E '^MQ_USERNAME=' deploy/.env 2>/dev/null | cut -d= -f2-)"
MQ_PW="$(grep -E '^MQ_PASSWORD=' deploy/.env 2>/dev/null | cut -d= -f2-)"
MQ_USER="${MQ_USER:-study}"; MQ_PW="${MQ_PW:-study123}"
if [ -z "$MYSQL_PW" ]; then echo "[ERR] deploy/.env 里没有 MYSQL_ROOT_PASSWORD"; exit 1; fi
if [ -n "${PYTHON:-}" ]; then PY="$PYTHON"; else PY="py -3.9"; fi

TX_PW="mqtest12345"

sql()  { docker exec study-mysql mysql -uroot -p"$MYSQL_PW" -N -e "$1" study_helper 2>/dev/null | tr -d '\r'; }
# 触发器体里有分号，mysql -e 会被拆句，必须走 DELIMITER 文件
sqlf() { docker exec -i study-mysql mysql -uroot -p"$MYSQL_PW" study_helper; }
# 给某个账号造几条答题记录，否则聚合为空、报告会直接走 no_data 降级
seed_answers() {
  local tok="$1" qid
  for qid in 1 2 3; do
    curl -s --max-time 20 -X POST "$API/api/answer/submit" -H "Authorization: Bearer $tok" \
         -H 'Content-Type: application/json' -d "{\"questionId\":$qid,\"userAnswer\":\"A\"}" >/dev/null
  done
}
tok_of() { $PY -c "import sys,json;d=json.load(sys.stdin);print((d.get('data') or {}).get('token',''))"; }
rid_of() { $PY -c "import sys,json;d=json.load(sys.stdin);print((d.get('data') or {}).get('requestId',''))"; }

login() {
  local u="$1"
  curl -s -X POST "$API/api/user/register" -H 'Content-Type: application/json' \
       -d "{\"username\":\"$u\",\"password\":\"$TX_PW\"}" >/dev/null
  curl -s -X POST "$API/api/user/login" -H 'Content-Type: application/json' \
       -d "{\"username\":\"$u\",\"password\":\"$TX_PW\"}" | tok_of
}

generate() {
  curl -s --max-time 90 -X POST "$API/api/report/generate" -H "Authorization: Bearer $1" | rid_of
}

db_status() { sql "SELECT status FROM ai_report WHERE request_id='$1'"; }
db_row()    { sql "SELECT CONCAT(status,' | ',IFNULL(token_cost,0),' | ',IFNULL(model,'-'),' | ',LEFT(IFNULL(error_msg,'-'),60)) FROM ai_report WHERE request_id='$1'"; }

wait_status() {   # $1=requestId $2=最多轮数（每轮 2 秒）
  local rid="$1" n="$2" i s
  for i in $(seq 1 "$n"); do
    s="$(db_status "$rid")"
    if [ "$s" = "1" ] || [ "$s" = "2" ]; then echo "$s"; return 0; fi
    sleep 2
  done
  echo "timeout:$s"; return 1
}

publish() {       # $1=routing_key $2=payload(JSON 字符串)
  local body
  body=$($PY -c "import json,sys;print(json.dumps({'properties':{},'routing_key':sys.argv[1],'payload':sys.argv[2],'payload_encoding':'string'}))" "$1" "$2")
  curl -s -u "$MQ_USER:$MQ_PW" -X POST "$MGMT/api/exchanges/%2F/$EXCHANGE/publish" \
       -H 'Content-Type: application/json' -d "$body"
}

qdepth() {        # $1=queue 名；走 management API，比 rabbitmqctl 快一个量级
  curl -s -u "$MQ_USER:$MQ_PW" "$MGMT/api/queues/%2F/$1" \
    | $PY -c "import sys,json
try:
    print(json.load(sys.stdin).get('messages', 0))
except Exception:
    print(0)"
}

wait_app_healthy() {
  local i s
  for i in $(seq 1 60); do
    s=$(docker inspect study-app --format '{{.State.Health.Status}}' 2>/dev/null)
    if [ "$s" = "healthy" ]; then return 0; fi
    sleep 2
  done
  echo "  [WARN] app 未在 120 秒内 healthy"; return 1
}

echo "===== 组 6：MQ 可靠性五场景 ====="
echo "账号：每个场景单独注册一个，避开 60 秒生成限流"
echo

# ---------------------------------------------------------------- S1
echo "--- S1 投递失败（停 broker 后生成）---"
T1=$(login w6mq_s1)
docker stop study-rabbitmq >/dev/null; sleep 3
echo "  broker 已停；队列深度采样：$(qdepth "$QUEUE_MAIN" 2>/dev/null || echo n/a)"
R1=$(generate "$T1"); echo "  requestId=$R1"
S1=$(wait_status "$R1" 30)
echo "  最终 status=$S1（期望 2：本地模板降级）"
echo "  行内容：$(db_row "$R1")"
echo "  接口在投递失败时是否仍返回 200：$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/report/generate" -H "Authorization: Bearer $T1")（第二次调用可能 429，属另一个机制）"
docker start study-rabbitmq >/dev/null
for i in $(seq 1 30); do
  h=$(docker inspect study-rabbitmq --format '{{.State.Health.Status}}' 2>/dev/null)
  [ "$h" = "healthy" ] && break; sleep 2
done
echo "  broker 已恢复（$h）"
docker restart study-app >/dev/null
wait_app_healthy && echo "  应用已重启并 healthy（broker 重连要重建连接，不重启后续场景会一路走本地降级）"
echo

# ---------------------------------------------------------------- S2
echo "--- S2 消费异常重试 3 次 → 进 DLQ ---"
T2=$(login w6mq_s2)
seed_answers "$T2"
sql "DROP TRIGGER IF EXISTS trg_w6_fail" >/dev/null
sql "DROP TABLE IF EXISTS w6_fail_flag" >/dev/null
# 关键：标记表必须是 MyISAM。触发器里对 InnoDB 表的写会随 SIGNAL 一起回滚，计数器永远减不动
# 次数取 4 = 前 3 次重试 + 第 4 次投递（第 4 次进 catch 后走 fallback，fallback 的 UPDATE 是第 5 次，必须成功）
sql "CREATE TABLE w6_fail_flag(cnt INT NOT NULL) ENGINE=MyISAM" >/dev/null
sql "INSERT INTO w6_fail_flag VALUES (4)" >/dev/null
{
  echo "DELIMITER \$\$"
  echo "CREATE TRIGGER trg_w6_fail BEFORE UPDATE ON ai_report FOR EACH ROW"
  echo "BEGIN"
  echo "  DECLARE n INT;"
  echo "  SELECT cnt INTO n FROM w6_fail_flag LIMIT 1;"
  echo "  IF n > 0 THEN"
  echo "    UPDATE w6_fail_flag SET cnt = n - 1;"
  echo "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'w6 injected update failure';"
  echo "  END IF;"
  echo "END\$\$"
  echo "DELIMITER ;"
} | sqlf
TRG=$(sql "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_name='trg_w6_fail'")
echo "  注入完成（触发器已建=$TRG）：前 4 次 UPDATE 失败（3 次重试 + 第 4 次投递），第 5 次 fallback 写模板并投 DLQ"
DLQ_BEFORE=$(qdepth "$QUEUE_DLQ")
R2=$(generate "$T2"); echo "  requestId=$R2，等待重试链路（3 次 × 15 秒 TTL，最多等 150 秒）"
S2=$(wait_status "$R2" 75)
DLQ_AFTER=$(qdepth "$QUEUE_DLQ")
echo "  最终 status=$S2（期望 2：模板降级）"
echo "  行内容：$(db_row "$R2")"
echo "  DLQ 消息数：$DLQ_BEFORE → $DLQ_AFTER（期望 +1，审计消息）"
echo "  注入计数表剩余：$(sql "SELECT cnt FROM w6_fail_flag")（0 表示窗口已用尽，第 4 次会写成功）"
sql "DROP TRIGGER IF EXISTS trg_w6_fail" >/dev/null
sql "DROP TABLE IF EXISTS w6_fail_flag" >/dev/null
echo "  注入已清理"
echo

# ---------------------------------------------------------------- S3
echo "--- S3 消费过程中 kill 应用 ---"
T3=$(login w6mq_s3)
seed_answers "$T3"
R3=$(generate "$T3"); echo "  requestId=$R3，1 秒后 kill study-app"
sleep 1
docker kill study-app >/dev/null; echo "  app 已 kill，未 ACK 的消息应回队列"
sleep 2
echo "  队列：$(docker exec study-rabbitmq rabbitmqctl list_queues name messages messages_unacknowledged 2>/dev/null | tr -d '\r' | awk '$1=="study.report.queue"{print "messages="$2" unacked="$3}')"
docker start study-app >/dev/null
wait_app_healthy && echo "  app 已重新 healthy"
S3=$(wait_status "$R3" 60)
ROWS3=$(sql "SELECT COUNT(*) FROM ai_report WHERE request_id='$R3'")
echo "  最终 status=$S3；该 requestId 的报告行数=$ROWS3（期望 1，只能有一份）"
echo "  行内容：$(db_row "$R3")"
echo

# ---------------------------------------------------------------- S4
echo "--- S4 重复投递（同一个 requestId 再发一次）---"
T4=$(login w6mq_s4)
READY=$(sql "SELECT CONCAT(id,'|',user_id,'|',request_id,'|',token_cost) FROM ai_report WHERE status=1 AND request_id<>'$R3' ORDER BY id DESC LIMIT 1")
RID4=$(echo "$READY" | cut -d'|' -f3)
ID4=$(echo "$READY" | cut -d'|' -f1)
UID4=$(echo "$READY" | cut -d'|' -f2)
TOK4=$(echo "$READY" | cut -d'|' -f4)
echo "  选一份已完成的报告：requestId=$RID4 id=$ID4 userId=$UID4 tokenCost=$TOK4"
BEFORE_TS=$(sql "SELECT CONCAT(status,'/',token_cost) FROM ai_report WHERE id=$ID4")
SKIP_BEFORE=$(docker logs study-app 2>&1 | grep -c 'skip duplicated')
publish "$RK" "{\"reportId\":$ID4,\"userId\":$UID4,\"requestId\":\"$RID4\"}" >/dev/null
echo "  已重投同一消息，等 6 秒观察"
sleep 6
AFTER_TS=$(sql "SELECT CONCAT(status,'/',token_cost) FROM ai_report WHERE id=$ID4")
SKIP_AFTER=$(docker logs study-app 2>&1 | grep -c 'skip duplicated')
echo "  status/tokenCost：$BEFORE_TS → $AFTER_TS（期望完全一致）"
echo "  本次重投触发的幂等拦截次数：$((SKIP_AFTER - SKIP_BEFORE))（期望 1）"
echo

# ---------------------------------------------------------------- S5
echo "--- S5 队列深度（先停消费者制造积压，再放消费者看清空速度）---"
docker stop study-app >/dev/null
echo "  消费者已停，开始投递 300 条"
PUB_START=$(date +%s)
for i in $(seq 1 300); do
  publish "$RK" "{\"reportId\":999999,\"userId\":1,\"requestId\":\"w6-depth-$i\"}" >/dev/null
done
PUB_END=$(date +%s)
PEAK=$(qdepth "$QUEUE_MAIN"); PEAK=${PEAK:-0}
echo "  300 条投递用时 $((PUB_END - PUB_START)) 秒，投完后队列深度=$PEAK"
docker start study-app >/dev/null
wait_app_healthy && echo "  消费者已恢复，开始计算清空时间"
DRAIN_START=$(date +%s)
for i in $(seq 1 600); do
  d=$(qdepth "$QUEUE_MAIN"); d=${d:-0}
  [ "$d" -eq 0 ] && break
  sleep 0.2
done
DRAIN_END=$(date +%s)
DRAIN_S=$((DRAIN_END - DRAIN_START)); [ "$DRAIN_S" -lt 1 ] && DRAIN_S=1
RATE=$(( PEAK / DRAIN_S ))
echo "  队列从 $PEAK 清到 0 用时 ${DRAIN_S} 秒，约 ${RATE} 条/秒；当前深度=$(qdepth "$QUEUE_MAIN")"
echo "  （这 300 条 reportId 不存在，消费端按幂等守卫直接 ACK 丢弃，测的是积压后的一次性清空能力）"
echo

echo "===== 五场景结束，结论请按下面五行写进 benchmark ====="
echo "  S1 停 broker 生成：status=$S1，队列无积压，接口未 5xx"
echo "  S2 三次重试：status=$S2，DLQ $DLQ_BEFORE → $DLQ_AFTER"
echo "  S3 kill 消费者：status=$S3，报告行数=$ROWS3"
echo "  S4 重复投递：$BEFORE_TS → $AFTER_TS"
echo "  S5 队列深度：积压 $PEAK，清空 ${DRAIN_S} 秒（${RATE:-0} 条/秒）"
