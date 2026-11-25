package io.github.pinkcarver8_blip.archfixture.ok.admin.web;

import io.github.pinkcarver8_blip.archfixture.ok.order.api.OrderApi;
import io.github.pinkcarver8_blip.archfixture.ok.order.api.OrderSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 허용: 관리자 컨트롤러는 admin 모듈에 있고 /api/admin 경로를 연다. */
@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController {

    private final OrderApi orderApi;

    AdminOrderController(OrderApi orderApi) {
        this.orderApi = orderApi;
    }

    @GetMapping("/{orderId}")
    OrderSummary find(@PathVariable long orderId) {
        return orderApi.find(orderId);
    }
}
