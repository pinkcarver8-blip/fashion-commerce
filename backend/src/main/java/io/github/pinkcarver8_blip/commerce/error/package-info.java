/**
 * 공유 모듈 error(ADR-001 보충). 오류 응답 형식(RFC 9457)과 오류 코드의 틀(ADR-010). 실제 오류 코드 목록은 각 업무 모듈이 가진다.
 *
 * <p>업무 처리(DB, {@code @Service}, {@code @Transactional})를 두지 않는다. 클래스와 공개 멤버는 허용 목록
 * ({@code src/test/resources/architecture/shared-modules-allowlist.txt})에 있는 것만 둔다. 어떤 업무 모듈에도, 다른 공유 모듈에도 의존하지 않는다.
 */
package io.github.pinkcarver8_blip.commerce.error;
