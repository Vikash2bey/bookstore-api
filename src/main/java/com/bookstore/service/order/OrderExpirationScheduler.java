package com.bookstore.service.order;

import com.bookstore.entity.OrderStatus;
import com.bookstore.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final OrderReservation orderReservation;
    private final OrderExpirationService orderExpirationService;

    @Scheduled(fixedDelayString = "${app.orders.expiration-check-interval:PT1M}")
    public void expireOrders() {
        LocalDateTime now = LocalDateTime.now();
        long afterId = 0;
        List<Long> orderIds;
        do {
            orderIds = orderRepository.findExpiredOrderIds(OrderStatus.PENDING_PAYMENT, now,
                    orderReservation.legacyCutoff(now), afterId, PageRequest.of(0, 100));
            for (Long orderId : orderIds) {
                try {
                    orderExpirationService.expireOrder(orderId);
                } catch (RuntimeException ex) {
                    log.error("Failed to expire order {}; will retry on the next check", orderId, ex);
                }
                afterId = orderId;
            }
        } while (orderIds.size() == 100);
    }
}
