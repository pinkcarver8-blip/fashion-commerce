package io.github.pinkcarver8_blip.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemoFailTest {

    @Test
    void 일부러_실패() {
        assertThat(1).isEqualTo(2);
    }
}
