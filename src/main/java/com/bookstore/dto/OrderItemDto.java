package com.bookstore.dto;

import lombok.Builder;

import java.math.BigDecimal;
@Builder
public record OrderItemDto(
        Long bookId,
        String title,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
