package com.rams.web.dto;

import com.rams.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull UserRole role
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record UserResponse(Long id, String name, String email, UserRole role, boolean active) {
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
