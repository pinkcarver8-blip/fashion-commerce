package io.github.pinkcarver8_blip.commerce.member.internal;

/** member 모듈이 고객에게 알릴 실패. 응답에는 {@link MemberErrorCode}의 코드와 문장만 나간다. */
public class MemberException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final MemberErrorCode code;

    public MemberException(MemberErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public MemberErrorCode code() {
        return code;
    }
}
