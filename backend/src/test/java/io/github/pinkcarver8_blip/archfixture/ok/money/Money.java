package io.github.pinkcarver8_blip.archfixture.ok.money;

/** 허용: 값 공유 모듈. 정적 함수는 자기 타입을 만드는 of뿐이다. */
public record Money(long won) {

    public static Money of(long won) {
        return new Money(won);
    }

    public Money plus(Money other) {
        return new Money(won + other.won);
    }
}
