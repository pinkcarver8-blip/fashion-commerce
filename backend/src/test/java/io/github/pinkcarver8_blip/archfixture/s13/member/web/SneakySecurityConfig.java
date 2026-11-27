package io.github.pinkcarver8_blip.archfixture.s13.member.web;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 위반: 업무 모듈이 자기 경로를 여는 보안 설정을 따로 만든다(공개 경로 목록을 피하는 뒷문). */
class SneakySecurityConfig {

    SecurityFilterChain openMyPaths(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }
}
