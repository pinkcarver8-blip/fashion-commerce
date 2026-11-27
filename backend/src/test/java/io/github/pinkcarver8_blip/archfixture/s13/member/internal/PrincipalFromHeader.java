package io.github.pinkcarver8_blip.archfixture.s13.member.internal;

import io.github.pinkcarver8_blip.archfixture.s13.identity.CustomerPrincipal;
import java.util.UUID;

/** 위반: 요청 헤더 값으로 주체를 만든다(남의 회원 ID로 행세할 수 있다). */
class PrincipalFromHeader {

    CustomerPrincipal fromHeader(String memberIdHeader) {
        return new CustomerPrincipal(UUID.fromString(memberIdHeader));
    }
}
