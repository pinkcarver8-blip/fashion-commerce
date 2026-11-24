package io.github.pinkcarver8_blip.archfixture.s05.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Product {

  @Id private Long id;

  private String name;

  protected Product() {}
}
