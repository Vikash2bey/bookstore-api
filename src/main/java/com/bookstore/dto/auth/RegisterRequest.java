package com.bookstore.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters") String username,
        @NotBlank(message = "Email is required") @Email(message = "Email should be valid") @Size(max = 255, message = "Email must be at most 255 characters") String email,
        @NotBlank(message = "Password is required") @Size(min = 6, message = "Password must have at least 6 characters") String password
) {}
