package com.bookstore.repository;

import com.bookstore.entity.Order;
import com.bookstore.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT o.id FROM Order o WHERE o.id > :afterId AND o.status = :status AND " +
            "((o.expiresAt IS NOT NULL AND o.expiresAt <= :now) OR " +
            "(o.expiresAt IS NULL AND o.createdAt <= :legacyCutoff)) ORDER BY o.id")
    List<Long> findExpiredOrderIds(@Param("status") OrderStatus status,
                                   @Param("now") LocalDateTime now,
                                   @Param("legacyCutoff") LocalDateTime legacyCutoff,
                                   @Param("afterId") Long afterId,
                                   Pageable pageable);
}
