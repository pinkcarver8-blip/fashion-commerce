package io.github.pinkcarver8_blip.archfixture.ok.catalog.internal;

import io.github.pinkcarver8_blip.commerce.money.AllowDecimal;

/** 허용: 검토된 표시(@AllowDecimal)를 단 곳만 소수 타입을 쓴다. 돈이 아니다. */
class RatingStats {

  @AllowDecimal(reason = "별점 평균. 돈이 아니다")
  private final double average;

  @AllowDecimal(reason = "별점 평균. 돈이 아니다")
  RatingStats(double average) {
    this.average = average;
  }

  @AllowDecimal(reason = "별점 평균. 돈이 아니다")
  double average() {
    return average;
  }
}
