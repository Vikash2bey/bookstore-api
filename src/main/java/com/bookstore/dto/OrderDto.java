package com.bookstore.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Builder
public record OrderDto(
        Long orderId,
        Long userId,
        String status,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        List<OrderItemDto> items
) {}
