#!/usr/bin/env bash
# R0-08: compose로 서비스 전부(R1-01부터 Mailpit 포함 여섯)를 띄워 R0-07 시나리오와 컨테이너 시간대(ADR-010)를 확인한다.
# 비밀번호는 실행할 때마다 무작위로 만들고, 끝나면 컨테이너와 데이터를 지운다.
set -euo pipefail

# CI 전용이다. 프로젝트 이름을 따로 써서, 혹시 내 컴퓨터에서 돌려도 로컬 데이터(commerce)를 지우지 않는다.
COMPOSE=(docker compose -p commerce-ci -f infra/docker-compose.yml)
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
reached_backend=no
for _ in $(seq 1 60); do
  code=$(curl -s -o /tmp/api.json -w '%{http_code}' http://localhost:3000/api/does-not-exist || true)
  # R1-01부터 목록에 없는 주소는 기본 거부(401)다(A5-9 S1). 백엔드의 오류 형식(code)으로 백엔드가 답했는지 본다.
  if [ "$code" = "401" ] && grep -q '"code":"UNAUTHENTICATED"' /tmp/api.json; then
    reached_backend=yes
    break
  fi
  sleep 2
done
# Nginx가 스스로 오류를 내도 숫자는 같을 수 있으므로, 백엔드의 JSON 응답을 받았는지로 판단한다.
[ "$reached_backend" = "yes" ] || fail "/api 요청이 백엔드에 닿지 않는다(마지막 응답 코드: ${code})"
grep -qiE 'trace|exception' /tmp/api.json && fail "오류 응답에 내부 정보가 있다"

# 로그인이 DB·Redis까지 닿는다(R1-01). 없는 계정이라 실패가 정답이다.
login_code=$(curl -s -o /tmp/login.json -w '%{http_code}' -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' -H 'X-Requested-With: XMLHttpRequest' \
  -d '{"email":"nobody@example.com","password":"not a real password"}' || true)
if [ "$login_code" != "401" ] || ! grep -q '"code":"LOGIN_FAILED"' /tmp/login.json; then
  fail "로그인 실패 응답이 정해 둔 모양이 아니다(응답 코드: ${login_code})"
fi

# 앱 전체 오류 응답(/error)에도 내부 정보가 없다. JSON이 아닌 본문으로 로그인하면 컨트롤러 밖에서 거절돼 /error로 간다.
error_code=$(curl -s -o /tmp/error.json -w '%{http_code}' -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: text/plain' -H 'X-Requested-With: XMLHttpRequest' -d 'x' || true)
case "$error_code" in 4??) ;; *) fail "잘못된 형식의 요청에 4xx가 아니다(응답 코드: ${error_code})" ;; esac
grep -qiE 'trace|exception' /tmp/error.json && fail "오류 응답(/error)에 내부 정보가 있다"

# 가짜 메일함은 화면(8025)만 이 컴퓨터에 열고, 메일 받는 포트(1025)는 열지 않는다(R1-01).
curl -fsS -o /dev/null http://localhost:8025/ || fail "가짜 메일함 화면이 열리지 않는다"
timeout 3 bash -c '</dev/tcp/127.0.0.1/1025' 2>/dev/null && fail "메일 받는 포트가 밖에 열려 있다"

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
