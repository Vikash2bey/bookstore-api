package com.bookstore.service.impl;

import com.bookstore.dto.AddCartItemRequest;
import com.bookstore.dto.CartDto;
import com.bookstore.dto.UpdateCartItemRequest;
import com.bookstore.entity.Book;
import com.bookstore.entity.Cart;
import com.bookstore.entity.CartItem;
import com.bookstore.entity.User;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.mapper.CartMapper;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CartMapper cartMapper;

    @Override
    @Transactional
    public CartDto getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return cartMapper.toDto(cart);
    }

    @Override
    @Transactional
    public CartDto addItemCart(Long userId, AddCartItemRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found"));

        if (!Boolean.TRUE.equals(book.getActive())) {
            throw new BadRequestException("Book is unavailable");
        }

        Cart cart = getOrCreateCart(user.getId());

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getBook().getId().equals(book.getId()))
                .findFirst()
                .orElse(null);

        if (existingItem == null) {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setBook(book);
            newItem.setQuantity(request.quantity());
            cart.getItems().add(newItem);
        } else {
            try {
                existingItem.setQuantity(Math.addExact(existingItem.getQuantity(), request.quantity()));
            } catch (ArithmeticException ex) {
                throw new BadRequestException("Cart item quantity is too large");
            }
        }

        cartRepository.save(cart);
        return cartMapper.toDto(cart);
    }

    @Override
    @Transactional
    public CartDto updateItemCart(Long userId, Long bookId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cart.getItems().stream()
                .filter(cartItem -> cartItem.getBook().getId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart"));

        item.setQuantity(request.quantity());
        return cartMapper.toDto(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public CartDto removeItemCart(Long userId, Long bookId) {
        Cart cart = getOrCreateCart(userId);
        boolean removed = cart.getItems().removeIf(item -> item.getBook().getId().equals(bookId));

        if (!removed) {
            throw new ResourceNotFoundException("Item not found in cart");
        }

        return cartMapper.toDto(cartRepository.save(cart));
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setItems(new ArrayList<>());
                    return cartRepository.save(cart);
                });
    }
}
