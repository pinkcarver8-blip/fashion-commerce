package io.github.pinkcarver8_blip.archfixture.s08.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Order {

  @Id private Long id;

  OrderStatus status;

  protected Order() {}

  /** 허용: Order 안의 도메인 메서드. */
  public void markPaid() {
    this.status = OrderStatus.PAID;
  }
}
