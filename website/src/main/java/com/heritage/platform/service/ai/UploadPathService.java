package com.heritage.platform.service.ai;

import com.heritage.platform.common.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class UploadPathService {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Value("${comfyui.output-directory:uploads/generated-covers}")
    private String generatedCoverOutputDirectory;

    public Path resolveUploadedPdf(String pdfUrl) {
        String value = pdfUrl == null ? "" : pdfUrl.trim();
        if (value.isEmpty()) {
            throw new BadRequestException("No PDF file is available for this publication.");
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            throw new BadRequestException("Only locally uploaded PDF files can be used for AI cover generation.");
        }
        if (!value.startsWith("/uploads/")) {
            throw new BadRequestException("Only files uploaded through the platform can be used for AI cover generation.");
        }

        Path uploadRoot = uploadRoot();
        String relativeName = value.substring("/uploads/".length()).replace("\\", "/");
        Path pdfPath = uploadRoot.resolve(relativeName).normalize();
        if (!pdfPath.startsWith(uploadRoot)) {
            throw new BadRequestException("Invalid uploaded PDF path.");
        }
        if (!Files.isRegularFile(pdfPath)) {
            throw new BadRequestException("No PDF file is available for this publication.");
        }
        if (!pdfPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BadRequestException("Only PDF files can be used for AI cover generation.");
        }
        return pdfPath;
    }

    public Path generatedCoverDirectory(Long publicationId) throws IOException {
        Path outputRoot = generatedCoverRoot();
        Path directory = outputRoot.resolve(String.valueOf(publicationId)).normalize();
        if (!directory.startsWith(outputRoot) || !directory.startsWith(uploadRoot())) {
            throw new BadRequestException("Generated cover output directory is invalid.");
        }
        Files.createDirectories(directory);
        return directory;
    }

    public Path existingGeneratedCoverDirectory(Long publicationId) {
        Path outputRoot = generatedCoverRoot();
        Path directory = outputRoot.resolve(String.valueOf(publicationId)).normalize();
        if (!directory.startsWith(outputRoot) || !directory.startsWith(uploadRoot())) {
            throw new BadRequestException("Generated cover output directory is invalid.");
        }
        return directory;
    }

    public Path resolveSelectedGeneratedCover(Long publicationId, String imageUrl) {
        String value = imageUrl == null ? "" : imageUrl.trim();
        if (value.isEmpty()) {
            throw new BadRequestException("Selected candidate image does not exist.");
        }
        if (value.startsWith("http://") || value.startsWith("https://") || !value.startsWith("/uploads/")) {
            throw new BadRequestException("Selected candidate image does not exist.");
        }
        Path expectedDirectory = existingGeneratedCoverDirectory(publicationId);
        Path uploadRoot = uploadRoot();
        Path candidate = uploadRoot.resolve(value.substring("/uploads/".length()).replace("\\", "/")).normalize();
        if (!candidate.startsWith(expectedDirectory) || !candidate.startsWith(uploadRoot)) {
            throw new BadRequestException("Selected candidate image does not exist.");
        }
        if (!Files.isRegularFile(candidate)) {
            throw new BadRequestException("Selected candidate image does not exist.");
        }
        if (!isSupportedImage(candidate.getFileName().toString())) {
            throw new BadRequestException("Selected candidate image does not exist.");
        }
        return candidate;
    }

    public String toUploadUrl(Path path) {
        Path uploadRoot = uploadRoot();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(uploadRoot)) {
            throw new BadRequestException("Generated cover path is invalid.");
        }
        return "/uploads/" + uploadRoot.relativize(normalized).toString().replace('\\', '/');
    }

    public Path uploadRoot() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path generatedCoverRoot() {
        String configured = generatedCoverOutputDirectory == null || generatedCoverOutputDirectory.isBlank()
                ? uploadRoot().resolve("generated-covers").toString()
                : generatedCoverOutputDirectory.trim();
        Path outputRoot = Path.of(configured).toAbsolutePath().normalize();
        if (!outputRoot.startsWith(uploadRoot())) {
            throw new BadRequestException("ComfyUI generated cover output directory must be inside the upload directory.");
        }
        return outputRoot;
    }

    private boolean isSupportedImage(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }
}
