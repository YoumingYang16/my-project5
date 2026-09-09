package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.entity.Post;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PublicationWorkflowValidationService {

    public void validateReadyForSubmission(Post post) {
        if (post == null || !post.getPublication()) {
            return;
        }

        String pdfUrl = clean(post.getPdfUrl());
        if (pdfUrl == null || !isUploadedPdf(pdfUrl)) {
            throw new BadRequestException("Please upload the publication PDF before submitting this publication.");
        }
    }

    private boolean isUploadedPdf(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return value.startsWith("/uploads/") && lower.endsWith(".pdf");
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
