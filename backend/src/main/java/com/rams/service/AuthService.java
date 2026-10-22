package com.rams.service;

import com.rams.domain.UserAccount;
import com.rams.domain.UserRole;
import com.rams.repository.UserRepository;
import com.rams.security.JwtService;
import com.rams.web.ApiException;
import com.rams.web.Text;
import com.rams.web.dto.AuthDtos.AuthResponse;
import com.rams.web.dto.AuthDtos.LoginRequest;
import com.rams.web.dto.AuthDtos.RegisterRequest;
import com.rams.web.dto.AuthDtos.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String name = Text.required(request.name(), 150, "Name");
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("An account with this email already exists");
        }
        UserAccount user = new UserAccount();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(true);
        users.save(user);
        return new AuthResponse(jwtService.createToken(user), toResponse(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null || !user.isActive()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return new AuthResponse(jwtService.createToken(user), toResponse(user));
    }

    public UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listActiveByRole(UserRole role) {
        return users.findByRoleAndActiveTrueOrderByNameAsc(role).stream()
                .map(this::toResponse)
                .toList();
    }

    private static String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase();
        if (!EMAIL.matcher(normalized).matches()) {
            throw ApiException.badRequest("Enter a valid email address");
        }
        return normalized;
    }
}
