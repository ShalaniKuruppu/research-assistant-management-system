package com.rams.service;

import com.rams.domain.UserAccount;
import com.rams.repository.UserRepository;
import com.rams.security.UserPrincipal;
import com.rams.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public UserAccount require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        UserAccount user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.unauthorized("Sign in required"));
        if (!user.isActive()) {
            throw ApiException.unauthorized("This account is inactive");
        }
        return user;
    }
}
