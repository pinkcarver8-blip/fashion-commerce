/**
 * 공유 모듈 platform(ADR-001 보충). 앱을 돌리는 설정: 로그인 검사 설정(고객·관리자 필터 체인, ADR-008), 전역 오류 처리기, 시간대·JSON 설정
 * (ADR-010).
 *
 * <p><b>아무 모듈도 이 모듈을 부를 수 없다.</b> 이 모듈은 {@code identity}·{@code error}만 부른다. 업무 처리({@code @Service},
 * {@code @Transactional}, DB)를 두지 않고, 클래스는 허용 목록에 있는 것만 둔다. 보안 설정이므로 바꿀 때는 작업진행.md A3-8 ②대로 멈추고 묻는다.
 */
package io.github.pinkcarver8_blip.commerce.platform;
