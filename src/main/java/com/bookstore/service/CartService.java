package com.bookstore.service;

import com.bookstore.dto.AddCartItemRequest;
import com.bookstore.dto.CartDto;
import com.bookstore.dto.UpdateCartItemRequest;

public interface CartService {
    CartDto getCart(Long userId);
    CartDto addItemCart(Long userId, AddCartItemRequest request);
    CartDto updateItemCart(Long userId, Long bookId, UpdateCartItemRequest request);
    CartDto removeItemCart(Long userId, Long bookId);
}

