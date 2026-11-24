package io.github.pinkcarver8_blip.archfixture.ok.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

/** QueryDSL이 이 엔티티의 Q클래스(QProduct)를 만든다. 규칙은 Q클래스를 잡지 않아야 한다. */
@Entity
@Getter
public class Product {

  @Id private Long id;

  private String name;

  protected Product() {}
}
