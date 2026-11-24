package io.github.pinkcarver8_blip.archfixture.s07.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 위반: 만든 뒤에 수량을 바꾸는 메서드가 있다. */
@Entity
public class OrderItem {

  @Id private Long id;

  int quantity;

  protected OrderItem() {}

  public OrderItem(Long id, int quantity) {
    this.id = id;
    this.quantity = quantity;
  }

  public void changeQuantity(int quantity) {
    this.quantity = quantity;
  }
}
