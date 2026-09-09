package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.request.LoginRequest;
import com.heritage.platform.dto.request.PasswordRecoveryQuestionLookupRequest;
import com.heritage.platform.dto.request.PasswordRecoveryResetRequest;
import com.heritage.platform.dto.request.RegisterRequest;
import com.heritage.platform.dto.response.AuthResponse;
import com.heritage.platform.dto.response.SecurityQuestionResponse;
import com.heritage.platform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("Registration completed successfully.", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Signed in successfully.", authService.login(request));
    }

    @PostMapping("/test-session")
    public ApiResponse<AuthResponse> testSession() {
        return ApiResponse.success("Test session created.", authService.createTestSession());
    }

    @PostMapping("/password-recovery/questions")
    public ApiResponse<List<SecurityQuestionResponse>> getPasswordRecoveryQuestions(
            @Valid @RequestBody PasswordRecoveryQuestionLookupRequest request
    ) {
        return ApiResponse.success(authService.getPasswordRecoveryQuestions(request));
    }

    @PostMapping("/password-recovery/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordRecoveryResetRequest request) {
        authService.resetPasswordBySecurityQuestions(request);
        return ApiResponse.success("Password reset successfully. Please sign in with your new password.", null);
    }
}
