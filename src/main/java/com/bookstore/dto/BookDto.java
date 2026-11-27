package com.bookstore.dto;

import lombok.Builder;

import java.math.BigDecimal;
@Builder
public record BookDto(
        Long id,
        String title,
        String author,
        BigDecimal price,
        Integer stock,
        Boolean active
) {}
