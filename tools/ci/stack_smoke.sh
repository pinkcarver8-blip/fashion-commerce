#!/usr/bin/env bash
# R0-08: compose로 다섯 서비스를 띄워 R0-07 시나리오와 컨테이너 시간대(ADR-010)를 확인한다.
# 비밀번호는 실행할 때마다 무작위로 만들고, 끝나면 컨테이너와 데이터를 지운다.
set -euo pipefail

COMPOSE=(docker compose -f infra/docker-compose.yml)
cleanup() { "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true; }
trap cleanup EXIT

# 비밀번호 파일이 없을 때만 만든다(CI는 매번 새로, 내 컴퓨터는 있는 것을 그대로 쓴다).
[ -f infra/.env ] || python3 infra/scripts/init-local-env.py
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 600

fail() { echo "실패: $1"; exit 1; }

for port in 3000 3001; do
  curl -fsS -o /dev/null "http://localhost:${port}/" || fail "${port} 화면이 열리지 않는다"
done

# 백엔드가 다 뜰 때까지 기다린다(최대 120초).
for _ in $(seq 1 60); do
  code=$(curl -s -o /tmp/api.json -w '%{http_code}' http://localhost:3000/api/does-not-exist || true)
  [ "$code" = "404" ] && grep -q '"status":404' /tmp/api.json && break
  sleep 2
done
[ "$code" = "404" ] || fail "/api 요청이 백엔드 404로 오지 않는다(받은 값: ${code})"
grep -qiE 'trace|exception' /tmp/api.json && fail "오류 응답에 내부 정보가 있다"

# grep -q가 먼저 끝나면 앞 명령이 출력 도중 끊겨 실패로 잡히므로(pipefail), 출력을 먼저 다 받은 뒤 찾는다.
redis_reply=$("${COMPOSE[@]}" exec -T redis redis-cli ping 2>&1 || true)
grep -q NOAUTH <<<"$redis_reply" || fail "Redis가 비밀번호 없이 응답한다"
curl -s --max-time 3 -o /dev/null http://localhost:8080/ && fail "백엔드 포트가 밖에 열려 있다"

for service in mysql redis backend; do
  tz=$("${COMPOSE[@]}" exec -T "$service" sh -c 'echo "$TZ"' | tr -d '\r')
  [ "$tz" = "UTC" ] || fail "${service} 컨테이너 시간대가 UTC가 아니다: ${tz}"
done
jvm_settings=$("${COMPOSE[@]}" exec -T backend java -XshowSettings:properties -version 2>&1 || true)
grep -q 'user.timezone = UTC' <<<"$jvm_settings" || fail "백엔드 JVM 시간대가 UTC가 아니다"

echo "스택 확인: 모두 통과"
