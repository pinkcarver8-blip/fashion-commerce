package io.github.pinkcarver8_blip.archfixture.s03.order.api;

import io.github.pinkcarver8_blip.archfixture.s03.order.domain.OrderMissing;

/** 위반: 공개 API가 내부 예외 타입을 throws로 드러낸다. */
public interface OrderLookup {
  long count() throws OrderMissing;
}
