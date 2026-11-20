#!/usr/bin/env bash
# W6 环境快照：每组实测前跑一次，输出贴进 evidence 开头
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1

MYSQL_PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' deploy/.env 2>/dev/null | cut -d= -f2-)"
if [ -z "${MYSQL_PW}" ]; then
  echo "[ERR] deploy/.env 里没有 MYSQL_ROOT_PASSWORD"; exit 1
fi

sec() { printf '\n----- %s -----\n' "$1"; }

sec "时间"
date '+%F %T %Z'

sec "机器 / 系统"
powershell -NoProfile -Command '$p=Get-CimInstance Win32_Processor|Select-Object -First 1; $c=Get-CimInstance Win32_ComputerSystem; $o=Get-CimInstance Win32_OperatingSystem; "{0} | {1} 核 | {2:N1} GB | {3} build {4}" -f $p.Name.Trim(),$p.NumberOfCores,($c.TotalPhysicalMemory/1GB),$o.Caption,$o.BuildNumber'

sec "Docker"
docker version --format 'server={{.Server.Version}} client={{.Client.Version}}'

sec "容器"
docker ps --format '{{.Names}}  {{.Status}}  {{.Image}}'

sec "应用镜像"
docker images study-helper/app:v1.0 --format '{{.Repository}}:{{.Tag}}  {{.ID}}  {{.Size}}'

sec "JVM"
docker exec study-app sh -c 'tr "\0" "\n" < /proc/1/environ | grep -E "JAVA_TOOL_OPTIONS|^TZ="'
docker exec study-app sh -c 'java -XX:+PrintFlagsFinal -version 2>&1 | grep -E " MaxHeapSize| MaxMetaspaceSize"'

sec "数据量"
docker exec study-mysql mysql -uroot -p"$MYSQL_PW" -N study_helper -e "
SELECT 'question', COUNT(*) FROM question
UNION ALL SELECT 'user', COUNT(*) FROM user
UNION ALL SELECT 'user_answer_record', COUNT(*) FROM user_answer_record
UNION ALL SELECT 'wrong_book', COUNT(*) FROM wrong_book
UNION ALL SELECT 'ai_report', COUNT(*) FROM ai_report;" 2>/dev/null

sec "题库分布"
docker exec study-mysql mysql -uroot -p"$MYSQL_PW" --default-character-set=utf8mb4 -N study_helper -e "
SELECT subject, chapter, COUNT(*) FROM question GROUP BY subject, chapter;" 2>/dev/null

sec "Redis"
docker exec study-redis redis-cli CONFIG GET maxmemory
docker exec study-redis redis-cli CONFIG GET maxmemory-policy
docker exec study-redis redis-cli INFO memory | grep -E "used_memory_human|maxmemory_human"

sec "RabbitMQ"
docker exec study-rabbitmq rabbitmqctl list_queues name messages messages_unacknowledged 2>/dev/null
docker exec study-rabbitmq rabbitmq-diagnostics -q environment 2>/dev/null | grep -i vm_memory_high_watermark

sec "question 索引"
docker exec study-mysql mysql -uroot -p"$MYSQL_PW" -N study_helper -e "SHOW INDEX FROM question;" 2>/dev/null | awk '{print $3, $5}' | sort -u