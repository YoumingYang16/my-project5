package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.dto.request.MyPasswordChangeRequest;
import com.heritage.platform.dto.request.MyProfileUpdateRequest;
import com.heritage.platform.dto.response.MyProfileResponse;
import com.heritage.platform.dto.response.SecurityQuestionResponse;
import com.heritage.platform.entity.User;
import com.heritage.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class MyProfileService {
    private static final int BIO_BYTE_LIMIT = 500;

    private final AuthContextService authContextService;
    private final UserRepository userRepository;
    private final SecurityQuestionService securityQuestionService;

    public MyProfileService(AuthContextService authContextService,
                            UserRepository userRepository,
                            SecurityQuestionService securityQuestionService) {
        this.authContextService = authContextService;
        this.userRepository = userRepository;
        this.securityQuestionService = securityQuestionService;
    }

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile() {
        return toResponse(authContextService.requireActiveUser());
    }

    @Transactional
    public MyProfileResponse updateMyProfile(MyProfileUpdateRequest request) {
        User user = authContextService.requireActiveUser();
        user.updateProfile(
                request.nickname().trim(),
                normalizeAvatarUrl(request.avatarUrl()),
                normalizeBio(request.bio())
        );
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<SecurityQuestionResponse> getMyPasswordSecurityQuestions() {
        return securityQuestionService.getMySecurityQuestionsForPasswordChange();
    }

    @Transactional
    public void changeMyPassword(MyPasswordChangeRequest request) {
        securityQuestionService.changeMyPasswordBySecurityQuestions(request.answers(), request.newPassword());
    }

    private String normalizeAvatarUrl(String avatarUrl) {
        if (avatarUrl == null) {
            return null;
        }
        String trimmed = avatarUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeBio(String bio) {
        if (bio == null) {
            return null;
        }
        String trimmed = bio.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (countBytes(trimmed) > BIO_BYTE_LIMIT) {
            throw new BadRequestException("Personal bio cannot exceed 500 bytes.");
        }
        return trimmed;
    }

    private int countBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private MyProfileResponse toResponse(User user) {
        return new MyProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getEmail(),
                user.getPhone(),
                user.getBio()
        );
    }
}
