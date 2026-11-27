package com.bookstore.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
@Builder
public record CartDto(
        Long cartId,
        Long userId,
        List<CartItemDto> items,
        BigDecimal totalAmount
) {}
