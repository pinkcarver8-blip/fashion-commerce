package io.github.pinkcarver8_blip.archfixture.ok.inventory.api;

public interface InventoryApi {
  boolean reserve(long skuId, int quantity);
}
