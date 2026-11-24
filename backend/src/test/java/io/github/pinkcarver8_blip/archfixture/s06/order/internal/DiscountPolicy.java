package io.github.pinkcarver8_blip.archfixture.s06.order.internal;

import io.github.pinkcarver8_blip.commerce.common.AllowDecimal;
import java.util.function.DoubleUnaryOperator;

/** 위반: 표시를 단 필드라도 표시 없는 메서드가 읽으면 실패한다. double 함수형 타입도 소수 타입이다. */
class DiscountPolicy {

  @AllowDecimal(reason = "시험용")
  private final double rate;

  private DoubleUnaryOperator adjust;

  @AllowDecimal(reason = "시험용")
  DiscountPolicy(double rate) {
    this.rate = rate;
  }

  long apply(long price) {
    return (long) (price * rate);
  }
}
