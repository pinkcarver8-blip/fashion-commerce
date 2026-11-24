package io.github.pinkcarver8_blip.archfixture.s06.order.internal;

/** 위반: 표시를 단 값이라도, 다른 클래스가 표시 없이 소수 타입 값을 받아 쓰면 실패한다. */
class RatingReader {

  long rounded(Rating rating) {
    return Math.round(rating.score());
  }
}
