package io.github.pinkcarver8_blip.archfixture.ok.identity;

/** 허용: 로그인한 사람 정보. 다른 모듈을 부르지 않는다. */
public record CustomerPrincipal(long memberId) {}
