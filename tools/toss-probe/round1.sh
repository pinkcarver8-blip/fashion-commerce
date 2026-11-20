#!/usr/bin/env bash
# R0-03 1회차. 서버(bootRun)를 띄운 뒤 tools/toss-probe에서 실행한다.
# 결제 인증 완료(AUTH_REDIRECT)를 감지하면 주문번호 접두사별 시나리오를 바로 실행한다.
# 승인은 인증 후 10분 안에 해야 하므로 사람이 손으로 부르지 않는다.
set -u
B=${PROBE_URL:-http://localhost:8080}/probe
LOG=out/events.log
mkdir -p out && touch "$LOG"

p() { echo "[$(date +%H:%M:%S)] $*"; }
call() { p "$1 $2 -> $(curl -s -X "$1" "$B$2")"; }

plan() {
  local id=$1
  case "$id" in
    r003-A*) # 정상 승인, 같은 키·다른 키 재승인, 취소, 같은 키·다른 키 재취소
      call GET "/payments/$id"
      call POST "/confirm/$id"
      call POST "/confirm/$id"
      call POST "/confirm/$id?idem=confirm-$id-other"
      call GET "/payments/$id"
      call POST "/cancel/$id"
      call POST "/cancel/$id"
      call POST "/cancel/$id?idem=cancel-$id-other"
      call GET "/payments/$id" ;;
    r003-B*) # 같은 멱등키 동시 승인(처리 중 409 확인)
      call POST "/confirm/$id" &
      call POST "/confirm/$id" &
      wait
      call GET "/payments/$id" ;;
    r0003C) # 승인하지 않고 상태 감시(30분 만료 기산점)
      call GET "/payments/$id"
      call POST "/watch/$id?intervalSec=60&maxMin=45" ;;
    r003-D*) # 인증 후 10분이 지나 승인
      call GET "/payments/$id"
      p "D: 10분 30초 기다린 뒤 승인"
      sleep 630
      call POST "/confirm/$id"
      call GET "/payments/$id" ;;
    r003-E*) # 우리 쪽 타임아웃(1ms) 뒤 상태 확인, 같은 키로 재승인
      call POST "/confirm/$id?timeoutMs=1"
      call GET "/payments/$id"
      sleep 3
      call GET "/payments/$id"
      call POST "/confirm/$id"
      call GET "/payments/$id" ;;
    r003-F*) # 토스에 틀린 금액으로 승인 요청, 같은 키로 맞는 금액, 새 키로 맞는 금액
      call POST "/confirm/$id?amount=100"
      call GET "/payments/$id"
      call POST "/confirm/$id"
      call GET "/payments/$id"
      call POST "/confirm/$id?idem=confirm-$id-2"
      call GET "/payments/$id" ;;
    *) p "계획 없음: $id" ;;
  esac
}

p "대기 중: $LOG"
tail -n 0 -F "$LOG" 2>/dev/null | while read -r line; do
  case "$line" in
    *AUTH_REDIRECT*)
      id=$(sed -E 's/.*orderId=([^ ]+).*/\1/' <<<"$line")
      p "인증 감지 $id"
      plan "$id" & ;;
    *PAGE_SDK_ERROR*|*PAGE_FAIL_REDIRECT*|*PAGE_PAY_CLICKED*|*WATCH_STATUS*|*WATCH_END*)
      p "${line#* (KST }" ;;
  esac
done
