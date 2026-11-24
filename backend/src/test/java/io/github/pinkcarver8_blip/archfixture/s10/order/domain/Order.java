package io.github.pinkcarver8_blip.archfixture.s10.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 위반: 엔티티에 Lombok equals·hashCode·toString. */
@Entity
@ToString
@EqualsAndHashCode
public class Order {

  @Id private Long id;

  protected Order() {}
}
