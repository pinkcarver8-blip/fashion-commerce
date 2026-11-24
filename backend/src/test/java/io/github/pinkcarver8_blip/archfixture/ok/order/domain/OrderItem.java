package io.github.pinkcarver8_blip.archfixture.ok.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

/** 허용: 필드는 생성자에서만 쓴다. */
@Entity
@Getter
public class OrderItem {

    @Id private Long id;

    private long unitPrice;

    private int quantity;

    protected OrderItem() {}

    public OrderItem(Long id, long unitPrice, int quantity) {
        this.id = id;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
}
