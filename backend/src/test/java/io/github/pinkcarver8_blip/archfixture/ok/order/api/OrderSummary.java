package io.github.pinkcarver8_blip.archfixture.ok.order.api;

/** 허용: 공개 API는 같은 모듈의 공개 타입(DTO record)만 드러낸다. */
public record OrderSummary(long id, String status) {}
