package io.github.pinkcarver8_blip.archfixture.s13.platform;

import io.github.pinkcarver8_blip.archfixture.s13.identity.CustomerPrincipal;
import java.util.UUID;

/** 허용: platform의 토큰 변환기는 주체를 만든다. */
class TokenConverter {

    CustomerPrincipal fromVerifiedSubject(String subject) {
        return new CustomerPrincipal(UUID.fromString(subject));
    }
}
