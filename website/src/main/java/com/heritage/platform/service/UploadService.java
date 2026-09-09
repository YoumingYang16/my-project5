package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.dto.response.UploadResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class UploadService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PDF_BYTES = 50L * 1024 * 1024;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private final AuthContextService authContextService;

    public UploadService(AuthContextService authContextService) {
        this.authContextService = authContextService;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Path.of(uploadDir));
    }

    public UploadResponse uploadImage(MultipartFile file) {
        authContextService.requireActiveUser();
        return storeImage(file);
    }

    public UploadResponse uploadPdf(MultipartFile file) {
        authContextService.requireActiveUser();
        return storePdf(file);
    }

    public UploadResponse uploadProfileAvatar(MultipartFile file) {
        authContextService.requireActiveUser();
        return storeImage(file);
    }

    private UploadResponse storeImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("Image exceeds the 10MB size limit.");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BadRequestException("Only image files can be uploaded.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        String generatedName = UUID.randomUUID() + extension;
        Path target = Path.of(uploadDir).resolve(generatedName);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BadRequestException("Image could not be saved: " + ex.getMessage());
        }

        return new UploadResponse(generatedName, "/uploads/" + generatedName);
    }

    private UploadResponse storePdf(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }

        if (file.getSize() > MAX_PDF_BYTES) {
            throw new BadRequestException("PDF exceeds the 50MB size limit.");
        }

        String extension = extractExtension(file.getOriginalFilename()).toLowerCase();
        String contentType = file.getContentType();
        boolean looksLikePdf = ".pdf".equals(extension)
                || "application/pdf".equalsIgnoreCase(contentType)
                || "application/x-pdf".equalsIgnoreCase(contentType);
        if (!looksLikePdf) {
            throw new BadRequestException("Only PDF files can be uploaded.");
        }

        String generatedName = UUID.randomUUID() + "-" + safePdfFilename(file.getOriginalFilename());
        Path target = Path.of(uploadDir).resolve(generatedName);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BadRequestException("PDF could not be saved: " + ex.getMessage());
        }

        return new UploadResponse(generatedName, "/uploads/" + generatedName);
    }

    private String safePdfFilename(String originalFilename) {
        String filename = originalFilename == null ? "publication.pdf" : originalFilename.replace('\\', '/');
        int slash = filename.lastIndexOf('/');
        if (slash >= 0) {
            filename = filename.substring(slash + 1);
        }
        String safe = filename.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (!safe.toLowerCase().endsWith(".pdf")) {
            safe += ".pdf";
        }
        if (safe.length() > 120) {
            safe = safe.substring(0, 116) + ".pdf";
        }
        return safe;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".jpg";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }
}
