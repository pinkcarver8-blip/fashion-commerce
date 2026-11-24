package io.github.pinkcarver8_blip.archfixture.ok.admin.internal;

import lombok.ToString;

/** 허용: 엔티티가 아닌 클래스의 @ToString은 막지 않는다. */
@ToString
class AdminNote {

    private final String text;

    AdminNote(String text) {
        this.text = text;
    }
}
