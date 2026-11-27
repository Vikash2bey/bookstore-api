package com.bookstore.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record LoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email should be valid") @Size(max = 255, message = "Email must be at most 255 characters") String email,
        @NotBlank(message = "Password is required") String password
) {}
