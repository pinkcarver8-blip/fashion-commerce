package io.github.pinkcarver8_blip.archfixture.s05.catalog.query;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.pinkcarver8_blip.archfixture.s05.catalog.domain.QProduct;

/** 위반: 조회 전용 패키지 안이라도 QueryDSL로 데이터를 바꾸지 않는다. */
class ProductRenameQuery {

    long rename(JPAQueryFactory queryFactory) {
        return queryFactory.update(QProduct.product).set(QProduct.product.name, "x").execute();
    }
}
