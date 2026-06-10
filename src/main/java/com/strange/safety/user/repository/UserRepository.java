package com.strange.safety.user.repository;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndStatus(String email, UserStatus status);

    Optional<User> findByIdAndStatus(Long id, UserStatus status);

    boolean existsByEmail(String email);

    Page<User> findByRoleInAndStatusNot(List<Role> roles, UserStatus status, Pageable pageable);
}
