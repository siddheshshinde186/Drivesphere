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
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleType")
    long countByRole(RoleType roleType);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleType")
    List<User> findAllByRole(RoleType roleType);
}
