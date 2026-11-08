#!/usr/bin/env bash
set -e
export MSYS_NO_PATHCONV=1
RAMP=${1:-60}
STAGE=${2:-300}
MODES=${3:-"NONE VALKEY CLIENT_SIDE"}
THREADS=${THREADS:-$((2 * $(nproc)))}
JAR=scrapper/target/scrapper-1.0-SNAPSHOT-exec.jar
mkdir -p load-tests/results

stop_scrapper() {
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'scrapper-1.0-SNAPSHOT-exec' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" || true
  sleep 3
}
stop_scrapper

for MODE in $MODES; do
  echo "===== режим $MODE ====="
  MASTER_PORT=$(docker compose exec -T valkey-sentinel-1 valkey-cli -p 26381 sentinel get-master-addr-by-name mymaster | tail -1 | tr -d '\r')
  docker compose exec -T valkey-net valkey-cli -p "$MASTER_PORT" flushall > /dev/null

  java -jar "$JAR" \
    --spring.datasource.url=jdbc:postgresql://localhost:5433/scrapper_load \
    --app.cache.mode="$MODE" \
    --app.scheduler.enable=false \
    --app.outbox.enable=false \
    --app.message-transport=HTTP \
    > "load-tests/results/scrapper-$MODE.log" 2>&1 &

  for i in $(seq 1 60); do
    if grep -q "Started ScrapperApplication" "load-tests/results/scrapper-$MODE.log"; then break; fi
    sleep 1
  done
  if ! grep -q "Started ScrapperApplication" "load-tests/results/scrapper-$MODE.log"; then
    echo "scrapper не запустился, см. load-tests/results/scrapper-$MODE.log"; exit 1
  fi

  java -cp load-tests/target/classes ru.tbank.load.LoadTest "$MODE" http://localhost:8081 "$THREADS" "$RAMP" "$STAGE" 1000 "load-tests/results/$MODE.md"

  stop_scrapper
done
echo "Готово, результаты в load-tests/results/"
