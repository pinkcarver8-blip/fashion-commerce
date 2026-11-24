package io.github.pinkcarver8_blip.archfixture.ok.order.internal;

import io.github.pinkcarver8_blip.archfixture.ok.catalog.api.CatalogApi;
import io.github.pinkcarver8_blip.archfixture.ok.money.Money;
import io.github.pinkcarver8_blip.archfixture.ok.order.api.OrderApi;
import io.github.pinkcarver8_blip.archfixture.ok.order.api.OrderSummary;
import io.github.pinkcarver8_blip.archfixture.ok.order.domain.Order;

/** 허용: order → catalog 공개 API(ADR-001 목록), 같은 모듈 내부, 공유 모듈 money. */
class OrderService implements OrderApi {

    private final CatalogApi catalogApi;

    OrderService(CatalogApi catalogApi) {
        this.catalogApi = catalogApi;
    }

    @Override
    public OrderSummary find(long orderId) {
        Order order = new Order(orderId);
        order.markPaid();
        Money price = Money.of(catalogApi.priceOf(1L));
        return new OrderSummary(price.won(), order.getStatus().name());
    }
}
