package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.request.LoginRequest;
import com.heritage.platform.dto.request.PasswordRecoveryQuestionLookupRequest;
import com.heritage.platform.dto.request.PasswordRecoveryResetRequest;
import com.heritage.platform.dto.request.RegisterRequest;
import com.heritage.platform.dto.response.AuthResponse;
import com.heritage.platform.dto.response.SecurityQuestionResponse;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.UserRepository;
import com.heritage.platform.util.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private static final String TEST_USERNAME = "paperlens-test-user";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final SecurityQuestionService securityQuestionService;

    @Value("${PAPERLENS_TEST_MODE:false}")
    private boolean testMode;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils,
                       SecurityQuestionService securityQuestionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.securityQuestionService = securityQuestionService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BadRequestException("This username is already in use.");
        }

        String email = normalizeOptional(request.email());
        String phone = normalizeOptional(request.phone());

        if (email != null && userRepository.existsByEmail(email)) {
            throw new BadRequestException("This email address is already registered.");
        }

        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new BadRequestException("This phone number is already registered.");
        }

        User user = userRepository.save(new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                null,
                UserRole.USER,
                Boolean.TRUE,
                email,
                phone
        ));
        securityQuestionService.replaceQuestionsForUser(user, request.securityQuestions());

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("The user could not be found."));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("This account has been disabled.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("The username or password is incorrect.");
        }

        return toResponse(user);
    }

    @Transactional
    public synchronized AuthResponse createTestSession() {
        if (!testMode) {
            throw new BadRequestException("Test mode is disabled.");
        }

        User user = userRepository.findByUsername(TEST_USERNAME).orElseGet(() -> userRepository.save(
                new User(
                        TEST_USERNAME,
                        passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                        "Test participant",
                        null,
                        UserRole.USER,
                        Boolean.TRUE,
                        null,
                        null,
                        "Shared test workspace account."
                )
        ));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<SecurityQuestionResponse> getPasswordRecoveryQuestions(PasswordRecoveryQuestionLookupRequest request) {
        return securityQuestionService.getQuestionsByUsername(request.username());
    }

    @Transactional
    public void resetPasswordBySecurityQuestions(PasswordRecoveryResetRequest request) {
        securityQuestionService.resetPasswordByUsername(
                request.username(),
                request.answers(),
                request.newPassword()
        );
    }

    private AuthResponse toResponse(User user) {
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole(),
                token,
                user.getEmail(),
                user.getPhone()
        );
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
