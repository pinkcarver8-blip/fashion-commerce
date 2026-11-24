/**
 * 커머스 백엔드의 최상위 패키지. 모듈은 바로 아래 패키지 하나씩이다(작업진행.md B1, ADR-001).
 *
 * <ul>
 *   <li>{@code <모듈>.api}: 다른 모듈이 부를 수 있는 공개 API. 다른 모듈은 이 패키지만 쓴다.
 *   <li>{@code <모듈>.query}: QueryDSL 읽기 조회 전용 클래스(ADR-007). QueryDSL은 여기서만 쓴다.
 *   <li>그 밖의 하위 패키지(엔티티, 리포지토리, 컨트롤러 등)는 모듈 내부다.
 *   <li>공유 모듈 {@code money}·{@code id}·{@code error}·{@code identity}: 업무 모듈이 같이 쓰는 값. 업무 처리를 두지 않고, 내용은 허용
 *       목록으로 고정한다(ADR-001 보충).
 *   <li>공유 모듈 {@code platform}: 앱을 돌리는 설정. 아무 모듈도 부를 수 없다.
 * </ul>
 *
 * <p>규칙은 {@code CommerceArchitectureRules}(테스트 코드)가 검사한다.
 */
package io.github.pinkcarver8_blip.commerce;
