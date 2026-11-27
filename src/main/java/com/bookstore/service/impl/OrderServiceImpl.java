package com.bookstore.service.impl;

import com.bookstore.dto.OrderDto;
import com.bookstore.entity.Book;
import com.bookstore.entity.Cart;
import com.bookstore.entity.Order;
import com.bookstore.entity.User;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.InsufficientStockException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.mapper.OrderMapper;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.OrderService;
import com.bookstore.service.order.OrderReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderReservation orderReservation;

    @Override
    @Transactional
    public OrderDto createOrder(Long userId, String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Order existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (existing != null) {
                log.warn("Order already exists for idempotency key {}", idempotencyKey);
                return orderMapper.toDto(existing);
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Cart is empty"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        List<Long> bookIds = cart.getItems().stream()
                .map(item -> item.getBook().getId())
                .toList();

        log.info("Creating new order for user {}", userId);
        Map<Long, Book> locked = bookRepository.findAllByIdForUpdate(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        Order order = new Order(user, idempotencyKey, orderReservation.newExpiry());
        cart.getItems().forEach(item -> {
            Book book = locked.get(item.getBook().getId());
            if (book == null) {
                throw new ResourceNotFoundException("Book not found: " + item.getBook().getId());
            }
            if (!book.hasStock(item.getQuantity())) {
                throw new InsufficientStockException("Insufficient stock for book " + book.getTitle() + ". Requested: " + item.getQuantity() + ", Available: " + book.getStock());

            }
            book.reduceStock(item.getQuantity());
            order.addItem(book, item.getQuantity());
        });

        Order savedOrder = orderRepository.save(order);
        cart.getItems().clear();
        cartRepository.save(cart);
        return orderMapper.toDto(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getOrdersForUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(orderMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return orderMapper.toDto(order);
    }

}
