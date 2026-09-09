package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.request.MyPasswordChangeRequest;
import com.heritage.platform.dto.request.MyProfileUpdateRequest;
import com.heritage.platform.dto.response.MyProfileResponse;
import com.heritage.platform.dto.response.SecurityQuestionResponse;
import com.heritage.platform.service.MyProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/my/profile")
public class MyProfileController {

    private final MyProfileService myProfileService;

    public MyProfileController(MyProfileService myProfileService) {
        this.myProfileService = myProfileService;
    }

    @GetMapping
    public ApiResponse<MyProfileResponse> getMyProfile() {
        return ApiResponse.success(myProfileService.getMyProfile());
    }

    @PutMapping
    public ApiResponse<MyProfileResponse> updateMyProfile(@Valid @RequestBody MyProfileUpdateRequest request) {
        return ApiResponse.success("Profile updated successfully.", myProfileService.updateMyProfile(request));
    }

    @GetMapping("/password-security-questions")
    public ApiResponse<List<SecurityQuestionResponse>> getMyPasswordSecurityQuestions() {
        return ApiResponse.success(myProfileService.getMyPasswordSecurityQuestions());
    }

    @PostMapping("/password/change")
    public ApiResponse<Void> changeMyPassword(@Valid @RequestBody MyPasswordChangeRequest request) {
        myProfileService.changeMyPassword(request);
        return ApiResponse.success("Password changed successfully.", null);
    }
}
