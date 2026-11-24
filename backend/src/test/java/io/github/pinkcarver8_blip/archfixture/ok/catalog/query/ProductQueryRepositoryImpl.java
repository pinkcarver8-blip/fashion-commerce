package io.github.pinkcarver8_blip.archfixture.ok.catalog.query;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.pinkcarver8_blip.archfixture.ok.catalog.domain.QProduct;
import java.util.List;

/** 허용: QueryDSL 읽기 조회는 조회 전용 패키지(query)에서 한다. */
class ProductQueryRepositoryImpl {

    private final JPAQueryFactory queryFactory;

    ProductQueryRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    List<String> names() {
        QProduct product = QProduct.product;
        return queryFactory.select(product.name).from(product).fetch();
    }
}
