package com.bookstore.dto;

import lombok.Builder;

import java.math.BigDecimal;
@Builder
public record CartItemDto(
        Long bookId,
        String title,
        String author,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal
) {}
