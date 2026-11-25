/**
 * 공유 모듈 identity(ADR-001 보충). 로그인한 사람 정보(고객·관리자 주체, ID와 용도만, ADR-008).
 *
 * <p>업무 처리(DB, {@code @Service}, {@code @Transactional})를 두지 않는다. 클래스와 공개 멤버는 허용 목록 ({@code
 * src/test/resources/architecture/shared-modules-allowlist.txt})에 있는 것만 둔다. 어떤 업무 모듈에도, 다른 공유 모듈에도
 * 의존하지 않는다.
 */
package io.github.pinkcarver8_blip.commerce.identity;
