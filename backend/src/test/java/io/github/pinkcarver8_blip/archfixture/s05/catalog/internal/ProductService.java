package io.github.pinkcarver8_blip.archfixture.s05.catalog.internal;

import io.github.pinkcarver8_blip.archfixture.s05.catalog.domain.QProduct;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;

/** 위반: 조회 전용 패키지(query) 밖에서 QueryDSL을 쓴다. */
class ProductService {

  List<String> names(JPAQueryFactory queryFactory) {
    return queryFactory.select(QProduct.product.name).from(QProduct.product).fetch();
  }
}
