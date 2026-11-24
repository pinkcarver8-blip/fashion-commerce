package io.github.pinkcarver8_blip.archfixture.s06.order.internal;

import io.github.pinkcarver8_blip.commerce.money.AllowDecimal;

/** 허용: 검토된 표시를 단 record. 이 클래스 안에서는 소수 타입을 쓸 수 있다. */
record Rating(@AllowDecimal(reason = "별점. 돈이 아니다") double score) {}
