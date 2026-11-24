package io.github.pinkcarver8_blip.archfixture.ok.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Objects;
import lombok.Getter;

/** 허용: 상태는 Order의 도메인 메서드만 바꾼다. equals·hashCode는 손으로 쓴 ID 기준이다. */
@Entity
@Getter
public class Order {

  @Id private Long id;

  private OrderStatus status;

  protected Order() {}

  public Order(Long id) {
    this.id = id;
    this.status = OrderStatus.PAYMENT_PENDING;
  }

  public void markPaid() {
    this.status = OrderStatus.PAID;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Order order && id != null && id.equals(order.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
