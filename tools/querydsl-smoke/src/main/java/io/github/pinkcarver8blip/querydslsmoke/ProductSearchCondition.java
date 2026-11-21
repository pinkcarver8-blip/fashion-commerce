package io.github.pinkcarver8blip.querydslsmoke;

/** 값이 null인 조건은 조회에서 빠진다. */
public record ProductSearchCondition(Long categoryId, Long minPrice, Long maxPrice) {
}
