package io.github.pinkcarver8blip.querydslsmoke;

import static io.github.pinkcarver8blip.querydslsmoke.QCategory.category;
import static io.github.pinkcarver8blip.querydslsmoke.QProduct.product;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

/** Spring Data가 이름 규칙(ProductSearchRepository + Impl)으로 찾아 ProductRepository에 붙인다. */
class ProductSearchRepositoryImpl implements ProductSearchRepository {

	private final JPAQueryFactory queryFactory;

	ProductSearchRepositoryImpl(JPAQueryFactory queryFactory) {
		this.queryFactory = queryFactory;
	}

	@Override
	public Page<ProductSummary> search(ProductSearchCondition condition, Pageable pageable) {
		BooleanExpression[] where = {
				categoryIdEq(condition.categoryId()),
				priceGoe(condition.minPrice()),
				priceLoe(condition.maxPrice()) };

		List<ProductSummary> content = queryFactory
				.select(Projections.constructor(ProductSummary.class,
						product.id, product.name, product.price, category.name))
				.from(product)
				.join(product.category, category)
				.where(where)
				.orderBy(product.price.asc(), product.id.asc())
				.offset(pageable.getOffset())
				.limit(pageable.getPageSize())
				.fetch();

		// 개수 조회는 필요할 때만 실행한다(첫 페이지가 다 안 찼으면 생략).
		return PageableExecutionUtils.getPage(content, pageable, () -> queryFactory
				.select(product.count())
				.from(product)
				.where(where)
				.fetchOne());
	}

	// where()는 null 조건을 건너뛴다. 그래서 값이 없으면 null을 돌려준다.
	private static BooleanExpression categoryIdEq(Long categoryId) {
		return categoryId == null ? null : product.category.id.eq(categoryId);
	}

	private static BooleanExpression priceGoe(Long minPrice) {
		return minPrice == null ? null : product.price.goe(minPrice);
	}

	private static BooleanExpression priceLoe(Long maxPrice) {
		return maxPrice == null ? null : product.price.loe(maxPrice);
	}
}
