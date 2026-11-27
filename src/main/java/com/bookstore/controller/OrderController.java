package com.bookstore.controller;

import com.bookstore.dto.OrderDto;
import com.bookstore.service.AuthService;
import com.bookstore.service.OrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class OrderController {

    private final AuthService authService;
    private final OrderService orderService;

    @PostMapping({"", "/checkout"})
    public ResponseEntity<OrderDto> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 255) String idempotencyKey,
            Authentication authentication) {

        Long userId = currentUserId(authentication);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(userId, idempotencyKey));
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getOrders(Authentication authentication) {
        return ResponseEntity.ok(orderService.getOrdersForUser(currentUserId(authentication)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable @Positive Long orderId, Authentication authentication) {
        return ResponseEntity.ok(orderService.getOrder(currentUserId(authentication), orderId));
    }

    private Long currentUserId(Authentication authentication) {
        String email = authentication.getName();
        return authService.getCurrentUserID(email);
    }
}
