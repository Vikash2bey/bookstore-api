package com.bookstore.service;

import com.bookstore.dto.OrderDto;

public interface PaymentService {
    OrderDto payForOrder(Long userId, Long orderId, String paymentMethod);
}
