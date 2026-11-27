package com.bookstore.service.payment;

import java.math.BigDecimal;

public interface CardPaymentGateway {
    boolean charge(Long orderId, BigDecimal amount);
}
