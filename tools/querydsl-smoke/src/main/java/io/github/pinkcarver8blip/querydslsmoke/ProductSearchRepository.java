package io.github.pinkcarver8blip.querydslsmoke;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductSearchRepository {

	/** 조건에 맞는 상품을 가격 오름차순으로 페이지 단위로 돌려준다. */
	Page<ProductSummary> search(ProductSearchCondition condition, Pageable pageable);
}
