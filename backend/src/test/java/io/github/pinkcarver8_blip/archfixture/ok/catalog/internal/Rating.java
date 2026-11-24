package io.github.pinkcarver8_blip.archfixture.ok.catalog.internal;

import io.github.pinkcarver8_blip.commerce.common.AllowDecimal;

/** 허용: record 구성 요소에 단 표시는 필드·접근자·생성자 매개변수에 모두 붙는다. */
record Rating(@AllowDecimal(reason = "별점. 돈이 아니다") double score) {}
