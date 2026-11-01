package com.drivesphere.repository;

import com.drivesphere.entity.RoleType;
import com.drivesphere.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT u FROM User u WHERE REPLACE(REPLACE(REPLACE(REPLACE(u.phoneNumber, ' ', ''), '-', ''), '+', ''), '(', '') = :cleanDigits")
    Optional<User> findByPhoneDigits(@org.springframework.data.repository.query.Param("cleanDigits") String cleanDigits);

    @Query("SELECT u FROM User u WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:identifier)) OR u.phoneNumber = :identifier")
    Optional<User> findByExactEmailOrPhone(@org.springframework.data.repository.query.Param("identifier") String identifier);

    default Optional<User> findByEmailOrPhone(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            return findByEmail(trimmed.toLowerCase());
        }
        String cleanDigits = trimmed.replaceAll("[^0-9]", "");
        String normalizedPhone = cleanDigits.length() == 10 ? "+91" + cleanDigits : (trimmed.startsWith("+") ? trimmed : "+" + cleanDigits);
        return findByEmailOrPhone(null, normalizedPhone, cleanDigits);
    }

    default Optional<User> findByEmailOrPhone(String email, String normalizedPhone, String cleanDigits) {
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = findByEmail(email.trim().toLowerCase());
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        if (normalizedPhone != null && !normalizedPhone.isBlank()) {
            Optional<User> byPhone = findByPhoneNumber(normalizedPhone);
            if (byPhone.isPresent()) {
                return byPhone;
            }
        }
        if (cleanDigits != null && !cleanDigits.isBlank()) {
            Optional<User> byDigits = findByPhoneDigits(cleanDigits);
            if (byDigits.isPresent()) {
                return byDigits;
            }
            if (cleanDigits.length() == 10) {
                return findByPhoneDigits("91" + cleanDigits);
            }
        }
        return Optional.empty();
    }

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleType")
    long countByRole(RoleType roleType);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleType")
    List<User> findAllByRole(RoleType roleType);

    long countByActiveTrue();
}
