package io.github.pinkcarver8blip.querydslsmoke;

/** 상품과 카테고리를 조인해 바로 받는 DTO. */
public record ProductSummary(Long productId, String name, long price, String categoryName) {
}
