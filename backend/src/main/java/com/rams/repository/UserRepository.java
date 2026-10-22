package com.rams.repository;

import com.rams.domain.UserAccount;
import com.rams.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findByRoleAndActiveTrueOrderByNameAsc(UserRole role);
}
