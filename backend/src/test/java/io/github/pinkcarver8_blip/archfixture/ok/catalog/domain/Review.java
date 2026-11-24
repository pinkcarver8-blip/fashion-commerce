package io.github.pinkcarver8_blip.archfixture.ok.catalog.domain;

import io.github.pinkcarver8_blip.commerce.money.AllowDecimal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

/** 허용: 엔티티의 표시 단 소수 필드. Lombok getter에도 표시가 복사된다(lombok.config). */
@Entity
@Getter
public class Review {

  @Id private Long id;

  @AllowDecimal(reason = "별점. 돈이 아니다")
  private double score;

  protected Review() {}
}
