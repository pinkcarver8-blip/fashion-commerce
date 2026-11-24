/**
 * 공유 모듈 money(ADR-001 보충). 금액 값({@code Money}, 원 단위 정수, ADR-010)과 소수 타입 예외 표시({@code AllowDecimal}).
 *
 * <p>업무 처리(DB, {@code @Service}, {@code @Transactional})를 두지 않는다. 클래스와 공개 멤버는 허용 목록
 * ({@code src/test/resources/architecture/shared-modules-allowlist.txt})에 있는 것만 둔다. 어떤 업무 모듈에도, 다른 공유 모듈에도 의존하지 않는다.
 */
package io.github.pinkcarver8_blip.commerce.money;
