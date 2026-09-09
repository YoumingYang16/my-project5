package com.heritage.platform.service.ai;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.service.AuthContextService;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;

@Service
public class CoverImageVariantService {
    private final AuthContextService auth;
    private final UploadPathService paths;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${renderer-api.base-url:http://127.0.0.1:3001}")
    private String rendererApiBaseUrl;

    public CoverImageVariantService(AuthContextService auth, UploadPathService paths, PostRepository postRepository) {
        this.auth = auth;
        this.paths = paths;
        this.postRepository = postRepository;
    }

    public String derive(Long publicationId, String imageUrl, Integer requestedWidth, Integer requestedHeight,
                         String requestedMode, Double requestedFocusX, Double requestedFocusY,
                         Double requestedCropX, Double requestedCropY,
                         Double requestedCropWidth, Double requestedCropHeight,
                         String backgroundColor) {
        User current = auth.requireActiveUser();
        Post publication = postRepository.findById(publicationId).orElseThrow(() -> new BadRequestException("The publication could not be found."));
        boolean owner = publication.getAuthor() != null && publication.getAuthor().getId().equals(current.getId());
        if (current.getRole() != UserRole.ADMIN && !owner) throw new com.heritage.platform.common.ForbiddenException("Only the publication uploader or an administrator can edit this image.");
        int width = bounded(requestedWidth, 900);
        int height = bounded(requestedHeight, 600);
        BufferedImage source;
        try {
            Path sourcePath = paths.resolveSelectedGeneratedCover(publicationId, imageUrl);
            source = ImageIO.read(sourcePath.toFile());
        } catch (Exception ex) {
            throw new BadRequestException("The selected source image could not be read.");
        }
        if (source == null) {
            throw new BadRequestException("The selected source image is not a supported bitmap.");
        }

        String mode = normalizeMode(requestedMode, source, width, height);
        double focusX = clamp(requestedFocusX == null ? 50 : requestedFocusX, 0, 100) / 100d;
        double focusY = clamp(requestedFocusY == null ? 50 : requestedFocusY, 0, 100) / 100d;
        if ("cover".equals(mode)) {
            double cropX = clamp(requestedCropX == null ? 0 : requestedCropX, 0, 95);
            double cropY = clamp(requestedCropY == null ? 0 : requestedCropY, 0, 95);
            double cropWidth = clamp(requestedCropWidth == null ? 100 : requestedCropWidth, 5, 100 - cropX);
            double cropHeight = clamp(requestedCropHeight == null ? 100 : requestedCropHeight, 5, 100 - cropY);
            int sourceX = Math.min(source.getWidth() - 1, (int) Math.round(source.getWidth() * cropX / 100d));
            int sourceY = Math.min(source.getHeight() - 1, (int) Math.round(source.getHeight() * cropY / 100d));
            int sourceWidth = Math.max(1, Math.min(source.getWidth() - sourceX,
                    (int) Math.round(source.getWidth() * cropWidth / 100d)));
            int sourceHeight = Math.max(1, Math.min(source.getHeight() - sourceY,
                    (int) Math.round(source.getHeight() * cropHeight / 100d)));
            source = source.getSubimage(sourceX, sourceY, sourceWidth, sourceHeight);
            focusX = 0.5d;
            focusY = 0.5d;
        }
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setColor(parseColor(backgroundColor));
        graphics.fillRect(0, 0, width, height);

        int drawWidth;
        int drawHeight;
        int x;
        int y;
        if ("stretch".equals(mode)) {
            drawWidth = width;
            drawHeight = height;
            x = 0;
            y = 0;
        } else {
            double scale = "contain".equals(mode)
                    ? Math.min((double) width / source.getWidth(), (double) height / source.getHeight())
                    : Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
            drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
            drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int maxX = Math.max(0, drawWidth - width);
            int maxY = Math.max(0, drawHeight - height);
            x = "contain".equals(mode) ? (width - drawWidth) / 2 : -(int) Math.round(maxX * focusX);
            y = "contain".equals(mode) ? (height - drawHeight) / 2 : -(int) Math.round(maxY * focusY);
        }
        graphics.drawImage(source, x, y, drawWidth, drawHeight, null);
        graphics.dispose();

        try {
            Path directory = paths.generatedCoverDirectory(publicationId).resolve("derived");
            Files.createDirectories(directory);
            Path target = directory.resolve("source-" + Instant.now().toEpochMilli() + "-" + width + "x" + height + "-" + mode + ".jpg");
            ImageIO.write(output, "jpg", target.toFile());
            return paths.toUploadUrl(target);
        } catch (Exception ex) {
            throw new BadRequestException("The edited image could not be saved.");
        }
    }

    public String createXhsCover(Long publicationId, String imageUrl, String headline,
                                 String textColor, String backgroundColor, Boolean showTitle, String templatePreset, String backgroundMode) {
        requireOwner(publicationId);
        Path source = paths.resolveSelectedGeneratedCover(publicationId, imageUrl);
        String safeHeadline = headline == null ? "" : headline.trim();
        if (safeHeadline.length() > 88) {
            throw new BadRequestException("Cover headline must not exceed 88 characters.");
        }
        ObjectNode body = objectMapper.createObjectNode();
        try {
            String mime = source.getFileName().toString().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            body.put("imageDataUrl", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(source)));
            body.put("coverHeadline", safeHeadline);
            body.put("title", safeHeadline);
            body.put("articleSlug", "publication-" + publicationId + "-custom-" + Instant.now().toEpochMilli());
            body.put("strategyLabel", "论文解读");
            body.put("sourceLabel", "AI/模板编辑 · 请人工核验");
            body.put("showTitle", showTitle == null || showTitle);
            body.put("textColor", validColor(textColor, "#202A31"));
            body.put("backgroundColor", validColor(backgroundColor, "#FFFFFF"));
            body.put("templatePreset", validPreset(templatePreset));
            body.put("backgroundMode", "solid");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rendererApiBaseUrl.replaceAll("/+$", "") + "/render/xhs-cover"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException("The Xiaohongshu cover renderer returned an error.");
            }
            JsonNode result = objectMapper.readTree(response.body());
            String encoded = result.path("imageDataBase64").asText();
            if (encoded.isBlank()) throw new BadRequestException("The renderer did not return an edited cover.");
            Path directory = paths.generatedCoverDirectory(publicationId).resolve("derived");
            Files.createDirectories(directory);
            Path target = directory.resolve("custom-xhs-" + Instant.now().toEpochMilli() + ".jpg");
            Files.write(target, Base64.getDecoder().decode(encoded));
            return paths.toUploadUrl(target);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("The edited Xiaohongshu cover could not be created.");
        }
    }

    private void requireOwner(Long publicationId) {
        User current = auth.requireActiveUser();
        Post publication = postRepository.findById(publicationId)
                .orElseThrow(() -> new BadRequestException("The publication could not be found."));
        boolean owner = publication.getAuthor() != null && publication.getAuthor().getId().equals(current.getId());
        if (current.getRole() != UserRole.ADMIN && !owner) {
            throw new com.heritage.platform.common.ForbiddenException("Only the publication uploader or an administrator can edit this image.");
        }
    }

    private String validColor(String value, String fallback) {
        return value != null && value.matches("^#[0-9A-Fa-f]{6}$") ? value : fallback;
    }

    private String validPreset(String value) {
        return switch (value == null ? "classic" : value) {
            case "image-focus", "text-focus" -> value;
            default -> "classic";
        };
    }

    private int bounded(Integer value, int fallback) {
        int result = value == null ? fallback : value;
        if (result < 256 || result > 2400) {
            throw new BadRequestException("Image width and height must be between 256 and 2400 pixels.");
        }
        return result;
    }

    private String normalizeMode(String mode, BufferedImage source, int width, int height) {
        String value = mode == null ? "smart" : mode.toLowerCase();
        if (!value.equals("cover") && !value.equals("contain") && !value.equals("smart") && !value.equals("stretch")) {
            throw new BadRequestException("Mode must be cover, contain, smart, or stretch.");
        }
        if (!value.equals("smart")) return value;
        double sourceRatio = (double) source.getWidth() / source.getHeight();
        double targetRatio = (double) width / height;
        return Math.abs(sourceRatio - targetRatio) > 0.45 ? "contain" : "cover";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Color parseColor(String value) {
        try {
            return Color.decode(value == null || !value.matches("^#[0-9A-Fa-f]{6}$") ? "#F5F5F5" : value);
        } catch (Exception ex) {
            return new Color(245, 245, 245);
        }
    }
}
