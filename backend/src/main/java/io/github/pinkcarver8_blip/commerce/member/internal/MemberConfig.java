package io.github.pinkcarver8_blip.commerce.member.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** member 모듈 설정. 관리자 모듈(R1-02)도 비밀번호 해시가 필요해지면 이 빈을 platform으로 옮길지 그때 정한다. */
@Configuration
class MemberConfig {

    /**
     * Spring Security 기본 인코더(위임 방식, ADR-008 결정 7). 7.2.0의 기본은 bcrypt다(PasswordEncoderFactories,
     * R1-01 확인). 저장값 앞에 {@code {bcrypt}}가 붙어, 나중에 방식을 바꿔도 옛 비밀번호가 동작한다.
     */
    @Bean
    PasswordEncoder memberPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
