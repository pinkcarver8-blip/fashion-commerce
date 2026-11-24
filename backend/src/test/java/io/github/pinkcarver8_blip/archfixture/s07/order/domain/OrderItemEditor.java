package io.github.pinkcarver8_blip.archfixture.s07.order.domain;

/** 위반: 다른 클래스가 주문상품 필드를 직접 쓴다. */
class OrderItemEditor {

  void edit(OrderItem item) {
    item.quantity = 3;
  }
}
