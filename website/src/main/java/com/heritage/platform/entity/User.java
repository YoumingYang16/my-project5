package com.heritage.platform.entity;

import com.heritage.platform.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 255)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 2000)
    private String bio;

    protected User() {
    }

    public User(String username, String passwordHash, String nickname, String avatarUrl, UserRole role, Boolean active) {
        this(username, passwordHash, nickname, avatarUrl, role, active, null, null, null);
    }

    public User(String username, String passwordHash, String nickname, String avatarUrl, UserRole role, Boolean active,
                String email, String phone) {
        this(username, passwordHash, nickname, avatarUrl, role, active, email, phone, null);
    }

    public User(String username, String passwordHash, String nickname, String avatarUrl, UserRole role, Boolean active,
                String email, String phone, String bio) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.role = role == null ? UserRole.USER : role;
        this.active = active == null ? Boolean.TRUE : active;
        this.email = email;
        this.phone = phone;
        this.bio = bio;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UserRole getRole() {
        return role;
    }

    public Boolean getActive() {
        return active;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getBio() {
        return bio;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updateProfile(String nickname, String avatarUrl, String bio) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }

    public void activate() {
        this.active = Boolean.TRUE;
    }

    public void deactivate() {
        this.active = Boolean.FALSE;
    }
}
