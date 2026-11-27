package com.bookstore.entity;

import com.bookstore.exception.InsufficientStockException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "books")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Boolean active;

    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        if (active == null) {
            active = true;
        }
        if (stock == null) {
            stock = 0;
        }
    }

    public boolean hasStock(int quantity) {
        return stock != null && stock >= quantity;
    }

    public void reduceStock(int quantity) {
        if (!hasStock(quantity)) {
            throw new InsufficientStockException("Not enough stock for book: " + title);
        }
        this.stock -= quantity;
    }
}
