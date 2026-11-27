package com.bookstore.service.payment;

import com.bookstore.dto.payment.PaymentRequest;

public interface PaymentAdapter {
    String getPaymentMethod();
    boolean pay(PaymentRequest request);
}
