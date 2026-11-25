package io.github.pinkcarver8_blip.archfixture.ok.platform;

import io.github.pinkcarver8_blip.archfixture.ok.error.ErrorCode;
import io.github.pinkcarver8_blip.archfixture.ok.identity.CustomerPrincipal;
import org.springframework.context.annotation.Configuration;

/** 허용: platform은 identity·error만 부르고, Spring 설정(@Configuration)을 둘 수 있다. */
@Configuration
class WebConfig {

    String describe(CustomerPrincipal principal, ErrorCode code) {
        return principal.memberId() + code.code();
    }
}
