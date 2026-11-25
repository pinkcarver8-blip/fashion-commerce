package io.github.pinkcarver8_blip.archfixture.s05.catalog.internal;

import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;

/** 위반: 이름만 Q로 시작하는 손으로 만든 클래스. 같은 패키지에 엔티티 Sneaky가 없다. */
class QSneaky extends EntityPathBase<Object> {

    private static final long serialVersionUID = 1L;

    QSneaky() {
        super(Object.class, "sneaky");
    }

    List<Object> all(JPAQueryFactory queryFactory) {
        return queryFactory.selectFrom(this).fetch();
    }
}
