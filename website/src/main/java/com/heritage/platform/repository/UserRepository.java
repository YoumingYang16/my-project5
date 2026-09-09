package com.heritage.platform.repository;

import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByUsername(String username);

    List<User> findAllByOrderByUpdatedAtDesc();

    List<User> findAllByUsernameContainingIgnoreCaseOrderByUpdatedAtDesc(String username);

    List<User> findAllByRoleOrderByUpdatedAtDesc(UserRole role);

    List<User> findAllByRoleAndUsernameContainingIgnoreCaseOrderByUpdatedAtDesc(UserRole role, String username);

    @Query("""
            select u from User u
            where (:username = '' or lower(u.username) like lower(concat('%', :username, '%')))
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            order by u.updatedAt desc
            """)
    List<User> searchAdminUsers(
            @Param("username") String username,
            @Param("role") UserRole role,
            @Param("active") Boolean active
    );

    @Query("""
            select u from User u
            where (:username = '' or lower(u.username) like lower(concat('%', :username, '%')))
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            """)
    Page<User> searchAdminUsers(
            @Param("username") String username,
            @Param("role") UserRole role,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
