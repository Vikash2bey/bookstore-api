package com.bookstore.service.order;

import com.bookstore.entity.Order;
import com.bookstore.entity.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Slf4j
public class OrderReservation {

    private final Duration duration;
    private final OrderStockRestorer stockRestorer;

    public OrderReservation(@Value("${app.orders.reservation-duration:PT15M}") String duration,
                            OrderStockRestorer stockRestorer) {
        this.duration = Duration.parse(duration);
        if (this.duration.isZero() || this.duration.isNegative()) {
            throw new IllegalArgumentException("Order reservation duration must be positive");
        }
        this.stockRestorer = stockRestorer;
    }

    public LocalDateTime newExpiry() {
        return LocalDateTime.now().plus(duration);
    }

    public LocalDateTime legacyCutoff(LocalDateTime now) {
        return now.minus(duration);
    }

    public boolean expireIfDue(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        LocalDateTime expiresAt = order.getExpiresAt() != null
                ? order.getExpiresAt() : order.getCreatedAt().plus(duration);
        if (LocalDateTime.now().isBefore(expiresAt)) {
            return false;
        }
        stockRestorer.restore(order);
        order.setStatus(OrderStatus.EXPIRED);
        log.info("Order {} expired and its stock was released", order.getId());
        return true;
    }
}
