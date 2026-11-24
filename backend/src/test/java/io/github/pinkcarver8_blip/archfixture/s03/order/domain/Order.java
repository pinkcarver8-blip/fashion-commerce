package io.github.pinkcarver8_blip.archfixture.s03.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Order {

  @Id private Long id;

  protected Order() {}

  public Long getId() {
    return id;
  }
}
