package com.bookstore.service.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class SimulatedCardPaymentGateway implements CardPaymentGateway {

    @Override
    public boolean charge(Long orderId, BigDecimal amount) {
        log.info("Processing simulated card payment for order {} with amount {}", orderId, amount);
        return true;
    }
}
