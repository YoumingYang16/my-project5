package com.heritage.platform.controller;

import com.heritage.platform.common.ApiResponse;
import com.heritage.platform.dto.ai.SocialCopyGenerationRequest;
import com.heritage.platform.dto.ai.SocialCopyGenerationResult;
import com.heritage.platform.service.ai.PublicationSocialCopyService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publications/{publicationId}/social-copy")
public class PublicationSocialCopyController {

    private final PublicationSocialCopyService socialCopyService;

    public PublicationSocialCopyController(PublicationSocialCopyService socialCopyService) {
        this.socialCopyService = socialCopyService;
    }

    @PostMapping("/generate")
    public ApiResponse<SocialCopyGenerationResult> generate(
            @PathVariable Long publicationId,
            @RequestBody(required = false) SocialCopyGenerationRequest request
    ) {
        return ApiResponse.success(
                "DeepSeek-generated social copy is based on extracted paper content.",
                socialCopyService.generate(publicationId, request)
        );
    }
}
