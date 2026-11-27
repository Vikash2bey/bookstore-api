package com.bookstore.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {
    public InsufficientStockException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_STOCK", message);
    }
}
