package com.bookstore.dto.payment;

import java.math.BigDecimal;

public record PaymentRequest(Long orderId, BigDecimal amount) {
}
