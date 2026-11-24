package io.github.pinkcarver8_blip.archfixture.ok.payment.internal;

import io.github.pinkcarver8_blip.archfixture.ok.inventory.api.InventoryApi;
import io.github.pinkcarver8_blip.archfixture.ok.order.api.OrderApi;

/** 허용: payment → order, payment → inventory 공개 API(ADR-001 목록). */
class PaymentService {

  private final OrderApi orderApi;
  private final InventoryApi inventoryApi;

  PaymentService(OrderApi orderApi, InventoryApi inventoryApi) {
    this.orderApi = orderApi;
    this.inventoryApi = inventoryApi;
  }

  boolean pay(long orderId) {
    return inventoryApi.reserve(1L, 1) && orderApi.find(orderId) != null;
  }
}
