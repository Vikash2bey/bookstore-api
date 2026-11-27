package com.bookstore.service.payment;

import com.bookstore.dto.payment.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardPaymentAdapter implements PaymentAdapter {

    private final CardPaymentGateway cardPaymentGateway;

    @Override
    public String getPaymentMethod() {
        return "CARD";
    }

    @Override
    public boolean pay(PaymentRequest request) {
        return cardPaymentGateway.charge(request.orderId(), request.amount());
    }
}
