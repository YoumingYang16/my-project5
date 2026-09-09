package com.heritage.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.dto.response.PublicationFigureCandidateResponse;
import com.heritage.platform.dto.response.PublicationFigureExtractionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class PublicationFigureExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(PublicationFigureExtractionService.class);
    private static final String ENGINE = "PDFFIGURES2";
    private static final String NOT_CONFIGURED_MESSAGE =
            "PDFFigures2 is required for publication teaser extraction but is not configured.";
    private static final String DISABLED_MESSAGE =
            "Automatic figure extraction is implemented but disabled in this local demo environment because PDFFigures2 is not deployed.";
    private static final String FAILED_MESSAGE =
            "Figure extraction failed. Please check PDFFigures2 configuration.";
    private static final String TIMEOUT_MESSAGE =
            "Figure extraction timed out. Please retry or check the PDFFigures2 service.";
    private static final String NO_CANDIDATES_MESSAGE =
            "No figure candidates were found in this PDF. Please upload a PDF with extractable figures or use the administrator fallback if enabled.";
    private static final Pattern FIGURE_NUMBER_PATTERN =
            Pattern.compile("(?i)\\b(?:fig\\.?|figure)\\s*([0-9]+[A-Za-z]?)");
    private static final Set<String> CAPTION_KEYWORDS = Set.of(
            "system", "framework", "overview", "architecture", "interface",
            "prototype", "pipeline", "workflow", "design", "method", "model", "result"
    );

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Value("${pdffigures2.enabled:false}")
    private boolean pdffigures2Enabled;

    @Value("${pdffigures2.command:}")
    private String pdffigures2Command;

    @Value("${pdffigures2.repo-dir:}")
    private String pdffigures2RepoDir;

    @Value("${pdffigures2.timeout-seconds:90}")
    private long timeoutSeconds;

    @Value("${pdffigures2.output-dir:uploads/figures}")
    private String pdffigures2OutputDir;

    private final AuthContextService authContextService;
    private final ObjectMapper objectMapper;

    public PublicationFigureExtractionService(
            AuthContextService authContextService,
            ObjectMapper objectMapper
    ) {
        this.authContextService = authContextService;
        this.objectMapper = objectMapper;
    }

    public PublicationFigureExtractionResponse extractFromUploadedPdf(String pdfUrl) {
        authContextService.requireActiveUser();
        if (!pdffigures2Enabled) {
            return PublicationFigureExtractionResponse.failure(DISABLED_MESSAGE);
        }
        Path pdfPath = resolveUploadedPdf(pdfUrl);

        List<String> commandPrefix = splitCommand(pdffigures2Command);
        if (commandPrefix.isEmpty()) {
            return PublicationFigureExtractionResponse.failure(NOT_CONFIGURED_MESSAGE);
        }

        Path uploadRoot = uploadRoot();
        Path outputRoot;
        try {
            outputRoot = resolveOutputRoot(uploadRoot);
        } catch (IllegalStateException ex) {
            logger.warn("PDFFigures2 output directory is invalid.", ex);
            return PublicationFigureExtractionResponse.failure(ex.getMessage());
        }

        try {
            String safeUploadId = safeUploadId(pdfPath);
            Path outputDir = outputRoot.resolve(safeUploadId).normalize();
            if (!outputDir.startsWith(outputRoot) || !outputDir.startsWith(uploadRoot)) {
                return PublicationFigureExtractionResponse.failure("PDFFigures2 figure output directory is invalid.");
            }
            Files.createDirectories(outputDir);

            String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
            Path imagePrefix = outputDir.resolve(runId + "-image-");
            Path dataPrefix = outputDir.resolve(runId + "-data-");
            Path statsPath = outputDir.resolve(runId + "-stats.json");

            List<String> command = new ArrayList<>(commandPrefix);
            command.add(pdfPath.toString());
            command.add("-q");
            command.add("-m");
            command.add(imagePrefix.toString());
            command.add("-d");
            command.add(dataPrefix.toString());
            command.add("-s");
            command.add(statsPath.toString());
            command.add("-f");
            command.add("png");

            ProcessResult processResult = runPdffigures2(command);
            if (processResult.startFailed()) {
                logger.warn("PDFFigures2 could not be started.", processResult.exception());
                return PublicationFigureExtractionResponse.failure(NOT_CONFIGURED_MESSAGE);
            }
            if (processResult.timedOut()) {
                logProcessOutput("PDFFigures2 timed out", processResult);
                return PublicationFigureExtractionResponse.failure(TIMEOUT_MESSAGE);
            }
            if (processResult.exitCode() != 0) {
                logProcessOutput("PDFFigures2 exited with code " + processResult.exitCode(), processResult);
                return PublicationFigureExtractionResponse.failure(FAILED_MESSAGE);
            }

            Path jsonOutput = findJsonOutput(outputDir, dataPrefix.getFileName().toString());
            if (jsonOutput == null) {
                logProcessOutput("PDFFigures2 completed without JSON output", processResult);
                return PublicationFigureExtractionResponse.failure(FAILED_MESSAGE);
            }

            List<PublicationFigureCandidateResponse> candidates = parseCandidates(jsonOutput, outputDir, uploadRoot);
            if (candidates.isEmpty()) {
                return PublicationFigureExtractionResponse.failure(NO_CANDIDATES_MESSAGE);
            }
            return PublicationFigureExtractionResponse.success(candidates);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("PDFFigures2 figure extraction failed.", ex);
            return PublicationFigureExtractionResponse.failure(FAILED_MESSAGE);
        }
    }

    private Path resolveUploadedPdf(String pdfUrl) {
        String value = pdfUrl == null ? "" : pdfUrl.trim();
        if (value.isEmpty()) {
            throw new BadRequestException("PDF URL is required.");
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            throw new BadRequestException("Only locally uploaded PDF files can be extracted.");
        }
        if (!value.startsWith("/uploads/")) {
            throw new BadRequestException("Only files uploaded through the platform can be extracted.");
        }

        String relativeName = value.substring("/uploads/".length()).replace("\\", "/");
        Path uploadRoot = uploadRoot();
        Path pdfPath = uploadRoot.resolve(relativeName).normalize();
        if (!pdfPath.startsWith(uploadRoot)) {
            throw new BadRequestException("Invalid uploaded PDF path.");
        }
        if (!Files.isRegularFile(pdfPath)) {
            throw new BadRequestException("The uploaded PDF could not be found.");
        }
        if (!pdfPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BadRequestException("Only PDF files can be extracted.");
        }
        return pdfPath;
    }

    private Path uploadRoot() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private Path resolveOutputRoot(Path uploadRoot) {
        String configured = pdffigures2OutputDir == null || pdffigures2OutputDir.isBlank()
                ? uploadRoot.resolve("figures").toString()
                : pdffigures2OutputDir.trim();
        Path outputRoot = Path.of(configured).toAbsolutePath().normalize();
        if (!outputRoot.startsWith(uploadRoot)) {
            throw new IllegalStateException("PDFFigures2 figure output directory must be inside the upload directory.");
        }
        return outputRoot;
    }

    private ProcessResult runPdffigures2(List<String> command) throws InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException | RuntimeException ex) {
            return new ProcessResult(-1, false, true, "", "", ex);
        }

        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        long effectiveTimeout = timeoutSeconds <= 0 ? 90 : timeoutSeconds;
        boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return new ProcessResult(-1, true, false, futureText(stdout), futureText(stderr), null);
        }

        return new ProcessResult(
                process.exitValue(),
                false,
                false,
                futureText(stdout),
                futureText(stderr),
                null
        );
    }

    private String readStream(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private String futureText(CompletableFuture<String> future) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return "";
        }
    }

    private Path findJsonOutput(Path outputDir, String dataPrefixFileName) throws IOException {
        try (Stream<Path> stream = Files.list(outputDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(dataPrefixFileName))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path);
                        } catch (IOException ex) {
                            return null;
                        }
                    }, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
        }
    }

    private List<PublicationFigureCandidateResponse> parseCandidates(
            Path jsonOutput,
            Path outputDir,
            Path uploadRoot
    ) throws IOException {
        JsonNode root = objectMapper.readTree(jsonOutput.toFile());
        List<JsonNode> figureNodes = new ArrayList<>();
        collectFigureNodes(root, figureNodes);

        List<Path> outputImages = listOutputImages(outputDir);
        List<CandidateDraft> drafts = new ArrayList<>();
        Set<String> seenImageUrls = new HashSet<>();
        for (int i = 0; i < figureNodes.size(); i++) {
            CandidateDraft draft = buildCandidate(figureNodes.get(i), i, outputImages, outputDir, uploadRoot);
            if (draft != null && seenImageUrls.add(draft.imageUrl())) {
                drafts.add(draft);
            }
        }

        drafts.sort(Comparator.comparingDouble(CandidateDraft::score).reversed());
        List<PublicationFigureCandidateResponse> responses = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            CandidateDraft draft = drafts.get(i);
            responses.add(new PublicationFigureCandidateResponse(
                    draft.imageUrl(),
                    draft.pageNumber(),
                    draft.figureNumber(),
                    draft.caption(),
                    draft.width(),
                    draft.height(),
                    Math.round(draft.score() * 10.0) / 10.0,
                    draft.reason(),
                    i == 0
            ));
        }
        return responses;
    }

    private void collectFigureNodes(JsonNode node, List<JsonNode> figureNodes) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject() && looksLikeFigureNode(node)) {
            figureNodes.add(node);
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectFigureNodes(child, figureNodes));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectFigureNodes(entry.getValue(), figureNodes));
        }
    }

    private boolean looksLikeFigureNode(JsonNode node) {
        return node.has("caption")
                || node.has("captionText")
                || node.has("renderURL")
                || node.has("imagePath")
                || node.has("figType")
                || node.has("figureType")
                || (node.has("page") && (node.has("captionBoundary") || node.has("regionBoundary")));
    }

    private CandidateDraft buildCandidate(
            JsonNode node,
            int index,
            List<Path> outputImages,
            Path outputDir,
            Path uploadRoot
    ) {
        Path imagePath = resolveImagePath(node, index, outputImages, outputDir, uploadRoot);
        if (imagePath == null) {
            return null;
        }

        String caption = normalizeWhitespace(jsonText(node, "caption", "captionText"));
        String figType = normalizeWhitespace(jsonText(node, "figType", "figureType", "type"));
        String figureNumber = figureNumber(node, caption, figType);
        Integer pageNumber = pageNumber(node);
        ImageSize imageSize = imageSize(imagePath, node);
        Score score = rankCandidate(figType, figureNumber, pageNumber, caption, imageSize);

        return new CandidateDraft(
                toFrontendUrl(imagePath, uploadRoot),
                pageNumber,
                figureNumber,
                caption,
                imageSize.width(),
                imageSize.height(),
                score.value(),
                score.reason()
        );
    }

    private Path resolveImagePath(
            JsonNode node,
            int index,
            List<Path> outputImages,
            Path outputDir,
            Path uploadRoot
    ) {
        for (String field : List.of("renderURL", "imageUrl", "imageURL", "imagePath", "path", "file", "fileName", "filename")) {
            Path path = pathFromJsonValue(jsonText(node, field), outputDir, uploadRoot);
            if (path != null) {
                return path;
            }
        }
        if (index >= 0 && index < outputImages.size()) {
            return outputImages.get(index);
        }
        return null;
    }

    private Path pathFromJsonValue(String value, Path outputDir, Path uploadRoot) {
        String trimmed = normalizeWhitespace(value);
        if (trimmed == null || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return null;
        }

        List<Path> candidates = new ArrayList<>();
        if (trimmed.startsWith("file:")) {
            try {
                candidates.add(Path.of(URI.create(trimmed)).toAbsolutePath().normalize());
            } catch (IllegalArgumentException ignored) {
                // Continue with non-URI interpretations below.
            }
        }
        if (trimmed.startsWith("/uploads/")) {
            candidates.add(uploadRoot.resolve(trimmed.substring("/uploads/".length())).normalize());
        }

        Path rawPath;
        try {
            rawPath = Path.of(trimmed);
        } catch (RuntimeException ex) {
            return null;
        }
        if (rawPath.isAbsolute()) {
            candidates.add(rawPath.normalize());
        } else {
            candidates.add(outputDir.resolve(rawPath).normalize());
            candidates.add(rawPath.toAbsolutePath().normalize());
            Path fileName = rawPath.getFileName();
            if (fileName != null) {
                candidates.add(outputDir.resolve(fileName).normalize());
            }
        }

        return candidates.stream()
                .filter(path -> safeOutputImage(path, outputDir, uploadRoot))
                .findFirst()
                .orElse(null);
    }

    private boolean safeOutputImage(Path path, Path outputDir, Path uploadRoot) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(uploadRoot)
                && normalized.startsWith(outputDir)
                && Files.isRegularFile(normalized);
    }

    private List<Path> listOutputImages(Path outputDir) throws IOException {
        try (Stream<Path> stream = Files.list(outputDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isImageFile(path.getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private ImageSize imageSize(Path imagePath, JsonNode node) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image != null) {
                return new ImageSize(image.getWidth(), image.getHeight());
            }
        } catch (IOException ex) {
            logger.debug("Could not read extracted figure image dimensions: {}", imagePath, ex);
        }

        Integer width = dimensionFromNode(node, "width", "x1", "x2");
        Integer height = dimensionFromNode(node, "height", "y1", "y2");
        return new ImageSize(width, height);
    }

    private Integer dimensionFromNode(JsonNode node, String dimension, String start, String end) {
        Integer direct = jsonInteger(node, dimension, dimension.substring(0, 1));
        if (direct != null && direct > 0) {
            return direct;
        }
        for (String field : List.of("regionBoundary", "renderBoundary", "boundary")) {
            JsonNode boundary = node.path(field);
            if (!boundary.isObject()) {
                continue;
            }
            Integer nested = jsonInteger(boundary, dimension, dimension.substring(0, 1));
            if (nested != null && nested > 0) {
                return nested;
            }
            Integer from = jsonInteger(boundary, start);
            Integer to = jsonInteger(boundary, end);
            if (from != null && to != null && Math.abs(to - from) > 0) {
                return Math.abs(to - from);
            }
        }
        return null;
    }

    private Score rankCandidate(
            String figType,
            String figureNumber,
            Integer pageNumber,
            String caption,
            ImageSize imageSize
    ) {
        double score = 0;
        List<String> reasons = new ArrayList<>();
        String lowerCaption = caption == null ? "" : caption.toLowerCase(Locale.ROOT);
        boolean table = isTable(figType, figureNumber, caption);
        if (table) {
            score -= 80;
            reasons.add("Candidate looks like a table");
        }

        boolean figureOne = isFigureOne(figureNumber, caption);
        if (figureOne && pageNumber != null && pageNumber <= 1) {
            score += 35;
            reasons.add("Figure 1 on first page");
        } else if (figureOne) {
            score += 24;
            reasons.add("Figure 1 candidate");
        }

        if (pageNumber != null) {
            if (pageNumber == 1) {
                score += 18;
                reasons.add("Appears on page 1");
            } else if (pageNumber == 2) {
                score += 14;
                reasons.add("Appears on page 2");
            } else if (pageNumber <= 4) {
                score += 6;
                reasons.add("Early-page figure");
            } else if (pageNumber > 8) {
                score -= Math.min(18, (pageNumber - 8) * 2.0);
            }
        }

        long area = area(imageSize);
        if (area >= 600_000) {
            score += 22;
            reasons.add("Large figure area");
        } else if (area >= 220_000) {
            score += 14;
            reasons.add("Large early-page figure");
        } else if (area > 0 && area < 30_000) {
            score -= 35;
            reasons.add("Very small image");
        }

        if (caption != null) {
            score += 8;
            reasons.add("Caption is available");
        }

        int keywordHits = 0;
        for (String keyword : CAPTION_KEYWORDS) {
            if (lowerCaption.contains(keyword)) {
                keywordHits++;
            }
        }
        if (keywordHits > 0) {
            score += Math.min(20, keywordHits * 7.0);
            reasons.add("Caption contains system/interface keywords");
        }

        Double aspectRatio = aspectRatio(imageSize);
        if (aspectRatio != null) {
            if (aspectRatio >= 0.75 && aspectRatio <= 2.4) {
                score += 10;
                reasons.add("Card-friendly aspect ratio");
            } else if (aspectRatio < 0.25 || aspectRatio > 5.0) {
                score -= 24;
                reasons.add("Logo/icon-like aspect ratio");
            }
        }

        if (looksLikeLogoOrIcon(imageSize)) {
            score -= 24;
            reasons.add("Logo/icon-like size");
        }

        String reason = reasons.isEmpty()
                ? "Ranked by page position, image size, and caption quality"
                : String.join("; ", reasons.stream().limit(3).toList());
        return new Score(score, reason);
    }

    private boolean isTable(String figType, String figureNumber, String caption) {
        String combined = String.join(" ",
                figType == null ? "" : figType,
                figureNumber == null ? "" : figureNumber,
                caption == null ? "" : caption
        ).trim().toLowerCase(Locale.ROOT);
        return combined.startsWith("table") || combined.contains(" table ");
    }

    private boolean isFigureOne(String figureNumber, String caption) {
        String value = figureNumber == null ? "" : figureNumber.toLowerCase(Locale.ROOT);
        if (value.matches("(figure\\s*)?1[a-z]?$")) {
            return true;
        }
        Matcher matcher = FIGURE_NUMBER_PATTERN.matcher(caption == null ? "" : caption);
        return matcher.find() && matcher.group(1).matches("1[a-z]?");
    }

    private boolean looksLikeLogoOrIcon(ImageSize imageSize) {
        Integer width = imageSize.width();
        Integer height = imageSize.height();
        if (width == null || height == null) {
            return false;
        }
        return width < 180 || height < 100 || area(imageSize) < 24_000;
    }

    private long area(ImageSize imageSize) {
        Integer width = imageSize.width();
        Integer height = imageSize.height();
        if (width == null || height == null || width <= 0 || height <= 0) {
            return 0;
        }
        return (long) width * height;
    }

    private Double aspectRatio(ImageSize imageSize) {
        Integer width = imageSize.width();
        Integer height = imageSize.height();
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null;
        }
        return width / (double) height;
    }

    private String figureNumber(JsonNode node, String caption, String figType) {
        String raw = normalizeWhitespace(jsonText(node, "figureNumber", "figNumber", "label", "name"));
        if (raw != null) {
            if (raw.toLowerCase(Locale.ROOT).startsWith("fig") || raw.toLowerCase(Locale.ROOT).startsWith("table")) {
                return raw;
            }
            String prefix = figType != null && figType.toLowerCase(Locale.ROOT).contains("table") ? "Table " : "Figure ";
            return prefix + raw;
        }
        Matcher matcher = FIGURE_NUMBER_PATTERN.matcher(caption == null ? "" : caption);
        if (matcher.find()) {
            return "Figure " + matcher.group(1);
        }
        return null;
    }

    private Integer pageNumber(JsonNode node) {
        Integer page = jsonInteger(node, "pageNumber", "page");
        if (page == null) {
            return null;
        }
        return page < 1 ? page + 1 : page;
    }

    private String jsonText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
            if (value.isNumber()) {
                return value.asText();
            }
        }
        return null;
    }

    private Integer jsonInteger(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isInt() || value.isLong()) {
                return value.asInt();
            }
            if (value.isNumber()) {
                return (int) Math.round(value.asDouble());
            }
            if (value.isTextual()) {
                try {
                    return Integer.valueOf(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Try the next field.
                }
            }
        }
        return null;
    }

    private String toFrontendUrl(Path imagePath, Path uploadRoot) {
        Path relative = uploadRoot.relativize(imagePath.toAbsolutePath().normalize());
        return "/uploads/" + relative.toString().replace('\\', '/');
    }

    private String safeUploadId(Path pdfPath) {
        String fileName = pdfPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String safe = baseName.replaceAll("[^A-Za-z0-9._-]", "-");
        return safe.isBlank() ? UUID.randomUUID().toString() : safe;
    }

    private List<String> splitCommand(String command) {
        String value = command == null ? "" : command.trim();
        if (value.isEmpty()) {
            return List.of();
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }
            if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }
            if (Character.isWhitespace(ch) && !inSingleQuotes && !inDoubleQuotes) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private void logProcessOutput(String context, ProcessResult result) {
        logger.warn("{}; stdout: {}; stderr: {}",
                context,
                truncate(result.stdout()),
                truncate(result.stderr()));
    }

    private String truncate(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 2000 ? text : text.substring(0, 2000) + "...";
    }

    private record CandidateDraft(
            String imageUrl,
            Integer pageNumber,
            String figureNumber,
            String caption,
            Integer width,
            Integer height,
            double score,
            String reason
    ) {
    }

    private record ImageSize(Integer width, Integer height) {
    }

    private record Score(double value, String reason) {
    }

    private record ProcessResult(
            int exitCode,
            boolean timedOut,
            boolean startFailed,
            String stdout,
            String stderr,
            Exception exception
    ) {
    }
}
