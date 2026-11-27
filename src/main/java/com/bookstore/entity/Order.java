package com.bookstore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(User user, String idempotencyKey, LocalDateTime expiresAt) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiry is required");
        this.status = OrderStatus.PENDING_PAYMENT;
        this.totalAmount = BigDecimal.ZERO;
    }

    public void addItem(Book book, int quantity) {
        Objects.requireNonNull(book, "book is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        OrderItem item = OrderItem.builder()
                .order(this)
                .book(book)
                .quantity(quantity)
                .unitPrice(Objects.requireNonNull(book.getPrice(), "book price is required"))
                .build();
        items.add(item);
        totalAmount = totalAmount.add(item.getLineTotal());
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OrderStatus.CREATED;
        }
    }

}
