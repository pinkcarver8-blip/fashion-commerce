package io.github.pinkcarver8_blip.commerce.identity;

import java.util.Objects;
import java.util.UUID;

/**
 * 로그인한 고객(ADR-008 결정 6). 회원의 외부 ID(무작위 UUID) 하나만 담는다. 이메일·이름·권한은 넣지 않는다.
 *
 * <p>이 값은 platform의 토큰 변환기가 검사를 통과한 토큰에서만 만든다(ArchUnit {@code onlyPlatformCreatesPrincipals}). 업무
 * 모듈은 요청 값으로 회원 ID를 받지 않고 이것만 쓴다(.claude/rules/shared-modules.md "공유 보안 설정의 약점과 막는 장치").
 */
public record CustomerPrincipal(UUID memberId) {

    public CustomerPrincipal {
        Objects.requireNonNull(memberId, "memberId");
    }
}
