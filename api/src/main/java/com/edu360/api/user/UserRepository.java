package com.edu360.api.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Lets the rest of the app look up, save, and check for User accounts in the database. */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByKeycloakId(String keycloakId);
}
