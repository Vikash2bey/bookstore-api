package com.bookstore.service.impl;

import com.bookstore.dto.OrderDto;
import com.bookstore.dto.payment.PaymentRequest;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderStatus;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.mapper.OrderMapper;
import com.bookstore.repository.OrderRepository;
import com.bookstore.service.PaymentService;
import com.bookstore.service.order.OrderReservation;
import com.bookstore.service.order.OrderStockRestorer;
import com.bookstore.service.payment.PaymentAdapter;
import com.bookstore.service.payment.PaymentAdapterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final PaymentAdapterFactory paymentAdapterFactory;
    private final OrderStockRestorer orderStockRestorer;
    private final OrderReservation orderReservation;

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public OrderDto payForOrder(Long userId, Long orderId, String paymentMethod) {
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (OrderStatus.PAID.equals(order.getStatus())) {
            return orderMapper.toDto(order);
        }

        if (OrderStatus.FAILED.equals(order.getStatus())) {
            throw new BadRequestException("This order has already failed and cannot be paid");
        }

        if (OrderStatus.EXPIRED.equals(order.getStatus())) {
            throw new BadRequestException("This order has expired and cannot be paid");
        }

        if (orderReservation.expireIfDue(order)) {
            throw new BadRequestException("This order has expired and cannot be paid");
        }

        if (paymentMethod == null || paymentMethod.isBlank()) {
            markOrderFailed(order, "Payment method is required");
            throw new BadRequestException("Payment method is required");
        }

        try {
            PaymentAdapter paymentAdapter = paymentAdapterFactory.getAdapter(paymentMethod);
            boolean paid = paymentAdapter.pay(
                    new PaymentRequest(order.getId(), order.getTotalAmount()));

            if (!paid) {
                markOrderFailed(order, "Payment failed or was rejected");
                throw new BadRequestException("Payment failed or was rejected");
            }

            order.setStatus(OrderStatus.PAID);
            return orderMapper.toDto(orderRepository.save(order));
        } catch (BadRequestException ex) {
            if (!OrderStatus.FAILED.equals(order.getStatus())) {
                markOrderFailed(order, ex.getMessage());
            }
            throw ex;
        }
    }

    private void markOrderFailed(Order order, String reason) {
        if (OrderStatus.FAILED.equals(order.getStatus())) {
            return;
        }
        orderStockRestorer.restore(order);
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        log.warn("Order {} marked as FAILED: {}", order.getId(), reason);
    }
}
