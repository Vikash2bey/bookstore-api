package com.bookstore.controller;

import com.bookstore.dto.OrderDto;
import com.bookstore.service.AuthService;
import com.bookstore.service.PaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PaymentController {

    private final AuthService authService;
    private final PaymentService paymentService;

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderDto> payOrder(
            @PathVariable @Positive Long orderId,
            @RequestParam(defaultValue = "CARD") @Size(max = 255) String paymentMethod,
            Authentication authentication) {
        return ResponseEntity.ok(paymentService.payForOrder(currentUserId(authentication), orderId, paymentMethod));
    }

    private Long currentUserId(Authentication authentication) {
        String email = authentication.getName();
        return authService.getCurrentUserID(email);
    }
}
