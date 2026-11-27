package com.bookstore.controller;

import com.bookstore.dto.AddCartItemRequest;
import com.bookstore.dto.CartDto;
import com.bookstore.dto.UpdateCartItemRequest;
import com.bookstore.service.AuthService;
import com.bookstore.service.CartService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final AuthService authService;

    @GetMapping("/cart")
    public ResponseEntity<CartDto> getCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.getCart(currentUserId(authentication)));
    }

    @PostMapping("/cart/items")
    public ResponseEntity<CartDto> addItem(@Valid @RequestBody AddCartItemRequest request,
                                           Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartService.addItemCart(currentUserId(authentication), request));
    }

    @PutMapping("/cart/items/{bookId}")
    public ResponseEntity<CartDto> updateItem(@PathVariable @Positive Long bookId,
                                              @Valid @RequestBody UpdateCartItemRequest request,
                                              Authentication authentication) {
        return ResponseEntity.ok(cartService.updateItemCart(currentUserId(authentication), bookId, request));
    }

    @DeleteMapping("/cart/items/{bookId}")
    public ResponseEntity<CartDto> removeItem(@PathVariable @Positive Long bookId,
                                              Authentication authentication) {
        return ResponseEntity.ok(cartService.removeItemCart(currentUserId(authentication), bookId));
    }

    private Long currentUserId(Authentication authentication) {
        String email = authentication.getName();
        return authService.getCurrentUserID(email);
    }
}
