package com.bookstore.service.order;

import com.bookstore.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final OrderReservation orderReservation;

    @Transactional
    public void expireOrder(Long orderId) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(orderReservation::expireIfDue);
    }
}
