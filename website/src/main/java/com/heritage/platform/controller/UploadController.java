package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.response.UploadResponse;
import com.heritage.platform.service.UploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/images")
    public ApiResponse<UploadResponse> uploadImage(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success("Image uploaded successfully.", uploadService.uploadImage(file));
    }

    @PostMapping("/pdfs")
    public ApiResponse<UploadResponse> uploadPdf(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success("PDF uploaded successfully.", uploadService.uploadPdf(file));
    }

    @PostMapping("/profile-avatar")
    public ApiResponse<UploadResponse> uploadProfileAvatar(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success("Avatar uploaded successfully.", uploadService.uploadProfileAvatar(file));
    }
}
