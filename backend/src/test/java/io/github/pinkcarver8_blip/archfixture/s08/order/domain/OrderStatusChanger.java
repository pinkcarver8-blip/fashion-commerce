package io.github.pinkcarver8_blip.archfixture.s08.order.domain;

/** 위반: Order 밖에서 주문 상태를 바꾼다. */
class OrderStatusChanger {

    void forcePaid(Order order) {
        order.status = OrderStatus.PAID;
    }
}
