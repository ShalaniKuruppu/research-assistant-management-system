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
import com.rams.web.dto.AuthDtos.UpdateProfileRequest;
import com.rams.web.dto.AuthDtos.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9() .\\-]{7,40}$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProfileImageService images;
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ProfileImageService images
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.images = images;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        UserAccount user = createAccount(
                request.name(),
                request.email(),
                request.password(),
                request.role(),
                null
        );
        return new AuthResponse(jwtService.createToken(user), toResponse(user));
    }

    @Transactional
    public UserResponse createResearchAssistant(String name, String email, String password, String phone) {
        UserAccount user = createAccount(name, email, password, UserRole.RESEARCH_ASSISTANT, phone);
        return toResponse(user);
    }

    private UserAccount createAccount(String name, String email, String password, UserRole role, String phone) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = Text.required(name, 150, "Name");
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("An account with this email already exists");
        }
        UserAccount user = new UserAccount();
        user.setName(normalizedName);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setPhone(phone == null ? null : normalizePhone(phone));
        user.setActive(true);
        return users.save(user);
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
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getProfileImageUrl(),
                user.getRole(),
                user.isActive()
        );
    }

    @Transactional
    public UserResponse updateProfile(UserAccount user, UpdateProfileRequest request) {
        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            if (!email.equals(user.getEmail()) && users.findByEmail(email).isPresent()) {
                throw ApiException.conflict("An account with this email already exists");
            }
            user.setEmail(email);
        }
        if (request.phone() != null) {
            user.setPhone(normalizePhone(request.phone()));
        }
        if (Boolean.TRUE.equals(request.clearProfileImage())) {
            images.deleteIfOwned(user.getProfileImageUrl());
            user.setProfileImageUrl(null);
        }
        users.save(user);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfilePicture(UserAccount user, MultipartFile file) {
        String previous = user.getProfileImageUrl();
        String stored = images.store(user.getId(), file);
        user.setProfileImageUrl(stored);
        users.save(user);
        images.deleteIfOwned(previous);
        return toResponse(user);
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

    private static String normalizePhone(String phone) {
        String value = Text.trimToNull(phone);
        if (value == null) {
            return null;
        }
        if (!PHONE.matcher(value).matches()) {
            throw ApiException.badRequest("Enter a valid contact number");
        }
        return value;
    }
}
