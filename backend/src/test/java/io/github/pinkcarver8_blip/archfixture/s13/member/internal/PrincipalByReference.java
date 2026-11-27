package io.github.pinkcarver8_blip.archfixture.s13.member.internal;

import io.github.pinkcarver8_blip.archfixture.s13.identity.CustomerPrincipal;
import java.util.Optional;
import java.util.UUID;

/** 위반: 생성자 참조(CustomerPrincipal::new)로 요청 값에서 주체를 만든다. */
class PrincipalByReference {

    Optional<CustomerPrincipal> fromHeader(String memberIdHeader) {
        return Optional.ofNullable(memberIdHeader)
                .map(UUID::fromString)
                .map(CustomerPrincipal::new);
    }
}
