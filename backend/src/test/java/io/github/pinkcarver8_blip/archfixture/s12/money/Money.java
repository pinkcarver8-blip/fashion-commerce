package io.github.pinkcarver8_blip.archfixture.s12.money;

/** 위반: 값 공유 모듈에 계산 함수(자기 타입을 만들지 않는 정적 함수)를 둔다. 허용 목록에도 없다. */
public record Money(long won) {

    public static long shippingFee(long total) {
        return total >= 50_000 ? 0 : 3_000;
    }
}
