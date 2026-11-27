package com.bookstore.dto.auth;

import lombok.Builder;

@Builder
public record AuthResponse(String token,String tokenType, Long userId, String email, String name) {
    public static AuthResponse tokenResponseData(String token, Long userId, String email, String name) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(userId)
                .email(email)
                .name(name)
                .build();
    }
}
