package com.heritage.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.request.CreateContributorApplicationRequest;
import com.heritage.platform.dto.response.MyContributorApplicationResponse;
import com.heritage.platform.service.ContributorApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/my/contributor-applications")
public class MyContributorApplicationController {

    private static final Logger logger = LoggerFactory.getLogger(MyContributorApplicationController.class);
    
    private final ContributorApplicationService contributorApplicationService;
    private final ObjectMapper objectMapper;
    private static final String UPLOAD_DIR = "uploads/contributor-applications";

    public MyContributorApplicationController(ContributorApplicationService contributorApplicationService, ObjectMapper objectMapper) {
        this.contributorApplicationService = contributorApplicationService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<MyContributorApplicationResponse>> listMyApplications() {
        return ApiResponse.success(contributorApplicationService.listMyApplications());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MyContributorApplicationResponse> createApplication(
            @RequestParam("request") String requestJson,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment
    ) throws IOException {
        logger.info("Received createApplication request - requestJson: {}, attachment: {}", 
            requestJson, 
            attachment != null ? attachment.getOriginalFilename() : "null");
        
        // Parse the request JSON
        CreateContributorApplicationRequest request;
        try {
            request = objectMapper.readValue(requestJson, CreateContributorApplicationRequest.class);
        } catch (Exception e) {
            logger.error("Failed to parse request JSON", e);
            return ApiResponse.failure("The request format is invalid.");
        }
            
        String attachmentPath = null;
        if (attachment != null && !attachment.isEmpty()) {
            if (!attachment.getContentType().equals("application/pdf")) {
                return ApiResponse.failure("Only PDF attachments are supported.");
            }
            attachmentPath = saveAttachment(attachment);
        }
        return ApiResponse.success("Contributor application submitted successfully.", contributorApplicationService.createApplication(request.applicationReason(), attachmentPath));
    }

    private String saveAttachment(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);

        Files.copy(file.getInputStream(), filePath);

        return UPLOAD_DIR + "/" + fileName;
    }
}
