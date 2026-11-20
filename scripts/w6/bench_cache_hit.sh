#!/usr/bin/env bash
# 组 3：缓存命中率（CONFIG RESETSTAT 差值法）
# 用法：bash scripts/w6/bench_cache_hit.sh "$TOKEN" [请求数，默认 1000]
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1

TOKEN="${1:-}"
N="${2:-1000}"
URL="http://localhost:8080/api/wrong/hot"
KEY="wrong:hot:top100"

if [ -z "$TOKEN" ]; then
  echo "[ERR] 用法: bash scripts/w6/bench_cache_hit.sh \"\$TOKEN\" [N]"
  exit 1
fi

MYSQL_PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' deploy/.env 2>/dev/null | cut -d= -f2-)"
if [ -z "${MYSQL_PW}" ]; then
  echo "[ERR] deploy/.env 里没有 MYSQL_ROOT_PASSWORD"; exit 1
fi

if [ -n "${PYTHON:-}" ]; then PY="$PYTHON"; else PY="py -3.9"; fi

redis_hits()   { docker exec study-redis redis-cli INFO stats | tr -d '\r' | awk -F: '/^keyspace_hits:/{print $2}'; }
redis_misses() { docker exec study-redis redis-cli INFO stats | tr -d '\r' | awk -F: '/^keyspace_misses:/{print $2}'; }
mysql_select() { docker exec study-mysql mysql -uroot -p"$MYSQL_PW" -N -e "SHOW GLOBAL STATUS LIKE 'Com_select'" 2>/dev/null | awk '{print $2}'; }

group() {
  local name="$1"
  local h0 m0 s0 h1 m1 s1 h m sel
  docker exec study-redis redis-cli CONFIG RESETSTAT >/dev/null
  s0=$(mysql_select); h0=$(redis_hits); m0=$(redis_misses)

  $PY scripts/w6/bench_http.py --url "$URL" --token "$TOKEN" -c 1 -n "$N" --label "cache-$name"

  h1=$(redis_hits); m1=$(redis_misses); s1=$(mysql_select)
  h=$((h1 - h0)); m=$((m1 - m0)); sel=$((s1 - s0))

  echo "  [$name] redis hits=$h misses=$m 命中率=$(awk -v a="$h" -v b="$m" 'BEGIN{t=a+b; if(t==0) printf "n/a"; else printf "%.2f%%", a*100/t}')  MySQL 回源 SELECT=$sel"
}

echo "===== 组 3：缓存命中率 /api/wrong/hot  n=$N  c=1 ====="
echo "口径说明：redis hits 里包含每次请求的 token 校验 GET，所以两组都会接近 100%；"
echo "          真正判断是否回源看 MySQL 的 Com_select 差值（冷组 1 次、热组 0 次）。"
echo

echo "[1/2] 冷组：清 key + 清计数器，再打 $N 次"
docker exec study-redis redis-cli DEL "$KEY" >/dev/null
group cold
echo
echo "[2/2] 热组：不清 key，直接再打 $N 次"
group hot
echo
echo "收尾检查（key 应存在且 TTL 在 30 分钟内）："
docker exec study-redis redis-cli EXISTS "$KEY"
docker exec study-redis redis-cli TTL "$KEY"
