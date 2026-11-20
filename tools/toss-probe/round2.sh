#!/usr/bin/env bash
# R0-03 2회차(웹훅·카드·모바일). 서버와 ngrok을 띄운 뒤 tools/toss-probe에서 실행한다.
# 웹훅은 내 테스트 상점에만 등록할 수 있으므로 W·I는 결제창(API 개별 연동 키) 주문으로 한다.
# 인증 완료(AUTH_REDIRECT)를 감지하면 주문번호 접두사별 시나리오를 바로 실행한다.
set -u
B=${PROBE_URL:-http://localhost:8080}/probe
LOG=out/events.log
mkdir -p out && touch "$LOG"
RETRY_ID=r003-I-webhook-retry
RETRY_SEEN=0

p() { echo "[$(date +%H:%M:%S)] $*"; }
call() { p "$1 $2 -> $(curl -s -X "$1" "$B$2")"; }

plan() {
  local id=$1
  case "$id" in
    r003-H*|r003-W*) # 카드 승인 → 웹훅 DONE 대기 → 취소 → 웹훅 CANCELED
      call POST "/confirm/$id"
      sleep 30
      call POST "/cancel/$id"
      call GET "/payments/$id" ;;
    r003-I*|r003-K*) # 승인만(I는 웹훅 500 응답 중)
      call POST "/confirm/$id"
      call GET "/payments/$id" ;;
    r003-J*) # 모바일 결제창 닫기용. 인증까지 갔다면 승인하지 않고 상태만 본다
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
    *PAGE_PAY_CLICKED*orderId=$RETRY_ID*)
      p "웹훅 응답을 500으로 바꾼다($RETRY_ID)"
      call POST "/webhook-mode?status=500" ;;
    *WEBHOOK_RECEIVED*)
      f=$(sed -E 's/.*file=([^ ]+).*/\1/' <<<"$line")
      body=$(cat "out/webhooks/$f" 2>/dev/null)
      oid=$(grep -oE '"orderId" *: *"[^"]*"' <<<"$body" | head -1 | sed -E 's/.*"([^"]*)"$/\1/')
      st=$(grep -oE '"status" *: *"[^"]*"' <<<"$body" | head -1 | sed -E 's/.*"([^"]*)"$/\1/')
      ev=$(grep -oE '"eventType" *: *"[^"]*"' <<<"$body" | head -1 | sed -E 's/.*"([^"]*)"$/\1/')
      p "웹훅 수신 $ev orderId=$oid status=$st (${line##*respond=}) $f"
      if [ "$oid" = "$RETRY_ID" ]; then
        RETRY_SEEN=$((RETRY_SEEN + 1))
        # 첫 전송 + 재전송 2번을 본 뒤 정상 응답으로 돌린다
        if [ "$RETRY_SEEN" -ge 3 ]; then
          p "웹훅 응답을 200으로 되돌린다"
          call POST "/webhook-mode?status=200"
        fi
      fi ;;
    *PAGE_SDK_ERROR*|*PAGE_FAIL_REDIRECT*|*PAGE_PAY_CLICKED*|*PAGE_CHECKOUT_RENDERED*)
      p "${line#* (KST }" ;;
  esac
done
