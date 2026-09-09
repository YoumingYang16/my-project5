package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.AiCoverGenerationOptions;
import com.heritage.platform.dto.ai.CandidateSceneScore;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RendererApiImageProvider implements PublicationImageProvider {

    private static final String RENDERER_MANIFEST = "renderer-manifest.json";
    private static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final UploadPathService uploadPathService;
    private QwenRuntimeApiKeyService qwenApiKeyService;
    private UserAiProviderSettingsService providerSettings;

    @Value("${renderer-api.enabled:true}")
    private boolean rendererEnabled;

    @Value("${renderer-api.base-url:http://127.0.0.1:3001}")
    private String rendererBaseUrl;

    @Value("${renderer-api.timeout-seconds:600}")
    private long timeoutSeconds;

    @Value("${renderer-api.image-provider-mode:dual}")
    private String imageProviderMode;

    @Value("${renderer-api.source-policy:balanced}")
    private String sourcePolicy;

    @Value("${renderer-api.aesthetic-profile:editorial}")
    private String aestheticProfile;

    @Value("${renderer-api.quality-mode:strict}")
    private String qualityMode;

    public RendererApiImageProvider(ObjectMapper objectMapper, UploadPathService uploadPathService) {
        this.objectMapper = objectMapper;
        this.uploadPathService = uploadPathService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setQwenApiKeyService(QwenRuntimeApiKeyService qwenApiKeyService) {
        this.qwenApiKeyService = qwenApiKeyService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setProviderSettings(UserAiProviderSettingsService providerSettings) {
        this.providerSettings = providerSettings;
    }

    @Override
    public String providerName() {
        return "renderer-api";
    }

    @Override
    public boolean enabled() {
        return rendererEnabled;
    }

    @Override
    public List<AiCoverCandidate> generate(PublicationImageGenerationRequest request) {
        if (!rendererEnabled) {
            throw new AiCoverWorkflowException("renderer-api is disabled.");
        }
        try {
            JsonNode extracted = extractPdf(request);
            ObjectNode renderRequest = buildRenderRequest(request, extracted);
            JsonNode renderResponse = postJson("/render/photo", renderRequest);
            List<JsonNode> selected = selectCandidates(extracted, renderResponse, request, renderRequest);
            if (selected.isEmpty()) {
                throw new AiCoverWorkflowException("renderer-api did not return usable image candidates.");
            }
            List<RenderedCandidate> persisted = persistCandidates(request, selected, renderResponse);
            List<AiCoverCandidate> candidates = toCompatibleCandidates(persisted);
            writeRendererManifest(request, renderRequest, extracted, renderResponse, candidates);
            return candidates;
        } catch (AiCoverWorkflowException ex) {
            throw ex;
        } catch (ConnectException ex) {
            throw new AiCoverWorkflowException(
                    "renderer-api is unavailable. Check RENDERER_API_BASE_URL and make sure the image service is running.",
                    ex
            );
        } catch (IOException ex) {
            throw new AiCoverWorkflowException("renderer-api candidates could not be saved.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiCoverWorkflowException("renderer-api generation timed out.", ex);
        }
    }

    private JsonNode extractPdf(PublicationImageGenerationRequest request) throws IOException, InterruptedException {
        byte[] pdf = Files.readAllBytes(request.pdfPath());
        String paperId = URLEncoder.encode(String.valueOf(request.publicationId()), StandardCharsets.UTF_8);
        HttpRequest httpRequest = HttpRequest.newBuilder(uri("/extract/pdf?id=" + paperId))
                .timeout(Duration.ofSeconds(effectiveTimeout()))
                .header("Content-Type", "application/pdf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(pdf))
                .build();
        HttpResponse<String> response = httpClient().send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(response.body());
    }

    private ObjectNode buildRenderRequest(PublicationImageGenerationRequest request, JsonNode extracted) {
        FinalPaperUnderstanding understanding = request.understanding();
        PaperEvidencePacket evidence = request.evidencePacket();
        AiCoverGenerationOptions options = request.options() == null ? AiCoverGenerationOptions.defaults() : request.options();
        String visualMode = classifyVisualMode(request, extracted);
        List<String> strategyAnchors = strategyEvidenceAnchors(
                visualMode, extracted == null ? "" : extracted.path("content").asText("")
        );
        List<String> mustShow = visualPhrases(uniqueStrings(
                understanding == null ? List.of() : understanding.mustShowElements(),
                request.sceneBrief() == null ? List.of() : request.sceneBrief().mustShowElements(),
                understanding == null ? List.of() : understanding.visualizableEntities(),
                strategyAnchors
        ), 3);
        List<String> mustAvoid = visualPhrases(uniqueStrings(
                understanding == null ? List.of() : understanding.mustAvoidElements(),
                understanding == null ? List.of() : understanding.forbiddenVisualElements(),
                request.sceneBrief() == null ? List.of() : request.sceneBrief().mustNotShowElements()
        ), 8);
        List<String> anchors = visualPhrases(uniqueStrings(
                understanding == null ? List.of() : understanding.importantSystemComponents(),
                evidence == null ? List.of() : evidence.domainTerms(),
                evidence == null ? List.of() : evidence.visualClues(),
                strategyAnchors
        ), 4);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", safe(request.title()));
        body.put("articleSlug", "publication-" + request.publicationId());
        body.put("briefStrategy", usesEvidenceGroundedPrompt(request) ? "evidence-grounded-v4" : "conservative-fallback");
        body.put("evidenceCharacterCount", evidence == null ? 0 : evidence.characterCount());
        body.put("pdfEvidenceSpanCount", evidence == null ? 0 : evidence.evidenceSpans().stream()
                .filter(span -> "PDF_TEXT".equalsIgnoreCase(span.source())).count());
        body.put("prompt", conciseVisualPrompt(request, visualMode, mustShow, anchors));
        List<String> promptVariants = evidenceGroundedPromptVariants(request);
        if (!promptVariants.isEmpty()) {
            body.set("prompts", objectMapper.valueToTree(promptVariants));
        }
        body.put("negativePrompt", conciseNegativePrompt(request, mustAvoid));
        body.put("visualMode", visualMode);
        body.put("imageProviderMode", options.normalizedImageProviderMode());
        body.put("sourcePolicy", options.normalizedSourcePolicy());
        body.put("aestheticProfile", normalizedAestheticProfile());
        body.put("qualityMode", normalizedQualityMode());
        UserAiProviderSettingsService.UserSettings selected = providerSettings == null
                ? UserAiProviderSettingsService.UserSettings.defaults() : providerSettings.current();
        String cloudProvider = selected.imageProvider();
        String cloudKey = selected.imageApiKey();
        String cloudModel = selected.imageModel();
        if ("qwen".equals(cloudProvider) && cloudKey.isBlank()) {
            cloudKey = qwenApiKeyService == null ? "" : qwenApiKeyService.currentRequestApiKey();
        }
        body.put("cloudImageProvider", cloudProvider);
        body.put("cloudImageModel", cloudModel);
        body.put("requireRequestImageKey", !"comfyui".equals(cloudProvider));
        if (!cloudKey.isBlank()) body.put("imageApiKey", cloudKey);
        if ("qwen".equals(cloudProvider) && !cloudKey.isBlank()) body.put("qwenApiKey", cloudKey);
        int[] outputSize = outputSize(options.normalizedOutputAspectRatio());
        body.put("outputProfile", options.normalizedOutputAspectRatio());
        body.put("showTitle", false);
        body.put("count", 2);
        body.put("width", outputSize[0]);
        body.put("height", outputSize[1]);
        // Two attempts preserve one quality retry without tripling cloud/GPU latency.
        body.put("maxAttempts", 2);
        body.put("referenceMode", options.normalizedReferenceMode());
        body.put("referenceStrength", 0.6);
        body.put("peoplePolicy", "optional");
        body.put("handsRequired", handsRequired(request, visualMode, mustShow, anchors));
        body.set("mustShow", objectMapper.valueToTree(mustShow));
        body.set("mustAvoid", objectMapper.valueToTree(mustAvoid));
        body.set("uniqueAnchors", objectMapper.valueToTree(anchors));
        body.set("compositionRoles", objectMapper.valueToTree("auto".equals(options.normalizedImageStyle()) ? compositionRoles(visualMode) : List.of(options.normalizedImageStyle())));
        body.set("evidence", objectMapper.valueToTree(evidenceSnippets(evidence)));
        body.set("sourceFigures", extracted.path("sourceFigures").isArray()
                ? extracted.path("sourceFigures") : objectMapper.createArrayNode());

        ObjectNode communicationBrief = body.putObject("communicationBrief");
        communicationBrief.put("briefVersion", "1.0");
        communicationBrief.put("paperType", visualMode);
        communicationBrief.put("audience", options.normalizedAudience());
        communicationBrief.put("communicationGoal", options.normalizedCommunicationGoal());
        communicationBrief.put("coreMessage", conciseText(coreMessage(request), 220));
        communicationBrief.set("mustShow", body.path("mustShow"));
        communicationBrief.set("mustAvoid", body.path("mustAvoid"));
        communicationBrief.set("uniqueAnchors", body.path("uniqueAnchors"));
        communicationBrief.set("evidence", body.path("evidence"));
        return body;
    }

    private int[] outputSize(String profile) {
        return switch (profile) {
            case "xiaohongshu-3:4" -> new int[]{960, 1280};
            case "square-1:1" -> new int[]{1024, 1024};
            case "wide-16:9" -> new int[]{1280, 720};
            default -> new int[]{900, 600};
        };
    }

    private List<JsonNode> selectCandidates(
            JsonNode extracted,
            JsonNode response,
            PublicationImageGenerationRequest request,
            ObjectNode baseRequest
    ) throws IOException, InterruptedException {
        List<JsonNode> source = arrayValues(extracted.path("sourceFigures"));
        source.sort(Comparator.comparingDouble(this::sourceCandidatePriority).reversed());
        List<JsonNode> generated = arrayValues(response.path("images"));
        String visualMode = baseRequest.path("visualMode").asText();
        if (visualMode.startsWith("algorithm") || "review-survey".equals(visualMode)) {
            generated.sort(Comparator.comparingInt(this::algorithmCandidateOrder));
        }

        List<JsonNode> selected = new ArrayList<>();
        if (!source.isEmpty() && !"ai-only".equals(normalizedSourcePolicy())) {
            selected.addAll(source);
        }
        selected.addAll(generated);
        selected = distinctByUrl(selected);

        if (generated.size() < 2) {
            ObjectNode additional = baseRequest.deepCopy();
            additional.put("imageProviderMode", "qwen");
            additional.put("sourcePolicy", "ai-only");
            additional.put("count", 1);
            additional.set("sourceFigures", objectMapper.createArrayNode());
            additional.set("compositionRoles", objectMapper.valueToTree(List.of("editorial-concept")));
            additional.put("prompt", baseRequest.path("prompt").asText()
                    + ", a distinct editorial composition focused on the paper-specific research object, no text");
            JsonNode additionalResponse = postJson("/render/photo", additional);
            selected.addAll(arrayValues(additionalResponse.path("images")));
            selected = distinctByUrl(selected);
        }
        return selected.stream().limit(52).toList();
    }

    private List<RenderedCandidate> persistCandidates(
            PublicationImageGenerationRequest request,
            List<JsonNode> selected,
            JsonNode renderResponse
    ) throws IOException, InterruptedException {
        Path outputDirectory = uploadPathService.generatedCoverDirectory(request.publicationId());
        List<RenderedCandidate> result = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            JsonNode image = selected.get(index);
            String remoteUrl = image.path("url").asText();
            if (remoteUrl.isBlank()) {
                continue;
            }
            byte[] imageBytes = downloadImage(remoteUrl);
            long seed = positiveSeed(image.path("seed").asLong(0));
            int candidateNumber = result.size() + 1;
            String fileName = "publication-" + request.publicationId()
                    + "-candidate-" + candidateNumber + "-seed-" + seed + ".jpg";
            Path target = outputDirectory.resolve(fileName).normalize();
            if (!target.startsWith(outputDirectory)) {
                throw new AiCoverWorkflowException("renderer-api candidate path is invalid.");
            }
            Files.write(target, imageBytes);
            List<String> candidateWarnings = new ArrayList<>(warnings(image));
            String socialCoverUrl = null;
            String candidateType = sourceType(image);
            // Keep all paper figures available, but avoid serially rendering a
            // second template image for every figure. Other templates remain
            // available on demand from the existing cover editor.
            boolean preRenderSocialCover = !"source-original".equals(candidateType) || candidateNumber == 1;
            if (preRenderSocialCover) {
                try {
                    socialCoverUrl = persistSocialCover(
                            request,
                            image,
                            remoteUrl,
                            outputDirectory,
                            candidateNumber
                    );
                } catch (RuntimeException | IOException | InterruptedException ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    candidateWarnings.add("The Xiaohongshu cover layout could not be generated; the website image is still available.");
                }
            }
            result.add(new RenderedCandidate(
                    image,
                    uploadPathService.toUploadUrl(target),
                    socialCoverUrl,
                    candidateType,
                    seed,
                    score(image),
                    List.copyOf(candidateWarnings),
                    image.path("prompt").asText(renderResponse.path("prompt").asText(""))
            ));
        }
        return result;
    }

    private List<AiCoverCandidate> toCompatibleCandidates(List<RenderedCandidate> persisted) {
        if (persisted.isEmpty()) {
            return List.of();
        }
        int bestIndex = 0;
        for (int index = 1; index < persisted.size(); index++) {
            if (persisted.get(index).score().totalScore() > persisted.get(bestIndex).score().totalScore()) {
                bestIndex = index;
            }
        }
        List<AiCoverCandidate> result = new ArrayList<>();
        for (int index = 0; index < persisted.size(); index++) {
            RenderedCandidate value = persisted.get(index);
            result.add(new AiCoverCandidate(
                    "candidate-" + (index + 1),
                    value.localUrl(),
                    value.seed(),
                    index == bestIndex,
                    value.prompt(),
                    value.score(),
                    recommendationReason(value.image(), value.score()),
                    value.warnings(),
                    value.socialCoverUrl(),
                    value.sourceType()
            ));
        }
        return List.copyOf(result);
    }

    private CandidateSceneScore score(JsonNode image) {
        boolean sourceCandidate = image.path("type").asText().startsWith("source");
        double quality = image.path("qualityScore").asDouble(sourceCandidate ? 0.92 : 0.7);
        double semantic = image.path("semanticScore").isNumber()
                ? image.path("semanticScore").asDouble()
                : sourceCandidate
                ? Math.min(1.0, image.path("relevanceScore").asDouble(image.path("sourceScore").asDouble(0.72)) + 0.12)
                : 0.72;
        double diversity = image.path("diversityScore").isNumber()
                ? image.path("diversityScore").asDouble() : sourceCandidate ? 0.90 : 0.75;
        boolean textRisk = image.path("textRisk").asBoolean(false);
        String anatomy = image.path("anatomyRisk").asText("none");
        boolean textReview = false;
        for (JsonNode warning : image.path("qualityWarnings")) {
            if ("possible-text-review".equals(warning.path("code").asText())) textReview = true;
        }
        int riskPenalty = textRisk ? 5 : "reject".equals(anatomy) ? 6 : "review".equals(anatomy) ? 3 : textReview ? 1 : 0;
        int realism = clampScore(quality * 10 - riskPenalty);
        int accuracy = clampScore(semantic * 10);
        int technology = clampScore(semantic * 10);
        int environment = clampScore((semantic * 0.7 + quality * 0.3) * 10);
        int clarity = clampScore(quality * 10 - (textRisk ? 4 : 0));
        int suitability = clampScore((quality * 0.6 + semantic * 0.4) * 10 - riskPenalty);
        int nonGeneric = clampScore(diversity * 10);
        int total = realism + accuracy + technology + environment + clarity + suitability + nonGeneric;
        List<String> problems = warnings(image);
        return new CandidateSceneScore(
                realism, accuracy, technology, environment, clarity, suitability, nonGeneric, total,
                problems,
                problems.isEmpty() ? "Passed local relevance and quality checks." : String.join("; ", problems)
        );
    }

    private String recommendationReason(JsonNode image, CandidateSceneScore score) {
        String type = image.path("type").asText("candidate");
        String provider = image.path("providerStrategy").asText(image.path("provider").asText("paper source"));
        if (type.startsWith("source")) {
            return "Paper figure candidate with direct source provenance; local suitability score "
                    + score.totalScore() + "/70.";
        }
        return "Paper-grounded " + provider + " candidate; local relevance and quality score "
                + score.totalScore() + "/70.";
    }

    private String persistSocialCover(
            PublicationImageGenerationRequest request,
            JsonNode image,
            String remoteUrl,
            Path outputDirectory,
            int candidateNumber
    ) throws IOException, InterruptedException {
        String type = sourceType(image);
        ObjectNode layoutRequest = objectMapper.createObjectNode();
        layoutRequest.put("url", remoteUrl);
        layoutRequest.put("title", conciseText(request.title(), 88));
        layoutRequest.put("coverHeadline", conciseText(request.title(), 42));
        layoutRequest.put("articleSlug", "publication-" + request.publicationId() + "-candidate-" + candidateNumber);
        layoutRequest.put("paperType", classifyVisualMode(request));
        layoutRequest.put("strategyLabel", humanStrategyLabel(classifyVisualMode(request)));
        layoutRequest.put("showTitle", true);
        layoutRequest.put("aiGenerated", !"source-original".equals(type));
        layoutRequest.put("sourceLabel", "source-original".equals(type)
                ? "论文原图 · 未经 AI 改写"
                : "基于论文证据生成 · AI 图片已标记");
        JsonNode response = postJson("/render/xhs-cover", layoutRequest);
        String socialRemoteUrl = response.path("image").path("url").asText();
        if (socialRemoteUrl.isBlank()) {
            throw new AiCoverWorkflowException("renderer-api did not return a Xiaohongshu cover.");
        }
        byte[] socialBytes = downloadImage(socialRemoteUrl);
        String fileName = "publication-" + request.publicationId()
                + "-candidate-" + candidateNumber + "-social.jpg";
        Path target = outputDirectory.resolve(fileName).normalize();
        if (!target.startsWith(outputDirectory)) {
            throw new AiCoverWorkflowException("renderer-api social cover path is invalid.");
        }
        Files.write(target, socialBytes);
        return uploadPathService.toUploadUrl(target);
    }

    private String sourceType(JsonNode image) {
        String details = (image.path("type").asText() + " "
                + image.path("provider").asText() + " "
                + image.path("providerStrategy").asText()).toLowerCase(Locale.ROOT);
        if (details.contains("source")) return "source-original";
        if (details.contains("qwen")) return "qwen";
        if (details.contains("doubao") || details.contains("seedream")) return "doubao";
        if (details.contains("comfy") || details.contains("ip-adapter")) return "comfyui";
        if (details.contains("method-summary") || details.contains("satori")) return "method-summary";
        return "generated";
    }

    private String humanStrategyLabel(String visualMode) {
        return switch (visualMode) {
            case "algorithm-computational" -> "ALGORITHM & METHOD";
            case "review-survey" -> "RESEARCH REVIEW";
            case "digital-heritage" -> "DIGITAL HERITAGE";
            case "audio-soundscape" -> "SOUNDSCAPE STUDY";
            case "hci-xr" -> "HCI & XR";
            case "hardware-wearable" -> "HARDWARE & WEARABLE";
            case "field-study" -> "FIELD STUDY";
            default -> "RESEARCH HIGHLIGHT";
        };
    }

    private byte[] downloadImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(effectiveTimeout()))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || response.body().length == 0 || response.body().length > MAX_IMAGE_BYTES) {
            throw new AiCoverWorkflowException("renderer-api returned invalid image data.");
        }
        return response.body();
    }

    private JsonNode postJson(String path, JsonNode body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(effectiveTimeout()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body),
                        StandardCharsets.UTF_8
                ))
                .build();
        HttpResponse<String> response = httpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String diagnostic = "";
            try {
                JsonNode error = objectMapper.readTree(response.body());
                diagnostic = error.path("message").asText(error.path("error").asText(""));
            } catch (IOException ignored) {
                // Keep the HTTP status below when a proxy returns a non-JSON error page.
            }
            diagnostic = conciseText(diagnostic, 500);
            throw new AiCoverWorkflowException("renderer-api returned HTTP " + response.statusCode()
                    + (diagnostic.isBlank() ? "." : ": " + diagnostic));
        }
        return objectMapper.readTree(response.body());
    }

    private void writeRendererManifest(
            PublicationImageGenerationRequest request,
            JsonNode renderRequest,
            JsonNode extraction,
            JsonNode response,
            List<AiCoverCandidate> candidates
    ) {
        try {
            Path directory = uploadPathService.generatedCoverDirectory(request.publicationId());
            Path manifest = directory.resolve(RENDERER_MANIFEST).normalize();
            if (!manifest.startsWith(directory)) {
                return;
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("manifestVersion", 1);
            value.put("publicationId", request.publicationId());
            value.put("generatedAt", Instant.now().toString());
            value.put("provider", providerName());
            ObjectNode safeRequest = renderRequest.deepCopy();
            safeRequest.remove(List.of("qwenApiKey", "imageApiKey", "apiKey", "authorization"));
            value.put("request", safeRequest);
            value.put("pdfExtraction", extraction);
            value.put("rendererResponse", response);
            value.put("localCandidates", candidates);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), value);
        } catch (IOException ignored) {
            // Candidate images remain usable even if diagnostic metadata cannot be persisted.
        }
    }

    private List<String> warnings(JsonNode image) {
        Set<String> warnings = new LinkedHashSet<>();
        JsonNode qualityWarnings = image.path("qualityWarnings");
        if (qualityWarnings.isArray()) {
            for (JsonNode warning : qualityWarnings) {
                String message = warning.isTextual() ? warning.asText() : warning.path("message").asText();
                if (!message.isBlank()) {
                    warnings.add(message);
                }
            }
        }
        if (image.path("textRisk").asBoolean(false)) {
            warnings.add("Possible generated text was detected.");
        }
        String anatomy = image.path("anatomyRisk").asText("none");
        if ("review".equals(anatomy) || "reject".equals(anatomy)) {
            warnings.add("Human anatomy requires manual review.");
        }
        return List.copyOf(warnings);
    }

    private int algorithmCandidateOrder(JsonNode image) {
        String type = image.path("type").asText();
        if ("method-summary".equals(type)) return 0;
        if (type.startsWith("qwen")) return 1;
        if (type.startsWith("doubao")) return 1;
        if (type.startsWith("comfy") || type.startsWith("ip-adapter")) return 2;
        return 3;
    }

    private List<JsonNode> distinctByUrl(List<JsonNode> values) {
        Set<String> urls = new LinkedHashSet<>();
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode value : values) {
            String url = value.path("url").asText();
            if (!url.isBlank() && urls.add(url)) {
                result.add(value);
            }
        }
        return result;
    }

    private List<JsonNode> arrayValues(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(values::add);
        }
        return values;
    }

    @SafeVarargs
    private final List<String> uniqueStrings(List<String>... sources) {
        Set<String> values = new LinkedHashSet<>();
        for (List<String> source : sources) {
            if (source == null) continue;
            source.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).forEach(values::add);
        }
        return List.copyOf(values);
    }

    private List<String> visualPhrases(List<String> values, int limit) {
        Set<String> phrases = new LinkedHashSet<>();
        for (String raw : values) {
            String value = safe(raw).replaceAll("\\s+", " ");
            if (value.isBlank()) continue;
            String[] parts = value.split("[;\\n]+");
            for (String part : parts) {
                String candidate = part.trim();
                String lower = candidate.toLowerCase(Locale.ROOT);
                if (candidate.length() > 150
                        || lower.startsWith("in this paper")
                        || lower.startsWith("this paper")
                        || lower.startsWith("our work")
                        || lower.startsWith("we present")
                        || lower.startsWith("the study")) continue;
                String phrase = conciseText(candidate, 120);
                if (phrase.length() < 3 || phrase.split("\\s+").length > 18) continue;
                phrases.add(phrase);
                if (phrases.size() >= limit) return List.copyOf(phrases);
            }
        }
        return List.copyOf(phrases);
    }

    private String conciseVisualPrompt(
            PublicationImageGenerationRequest request,
            String visualMode,
            List<String> mustShow,
            List<String> anchors
    ) {
        if (usesEvidenceGroundedPrompt(request)) {
            return safe(request.positivePrompt()).replaceAll("\s+", " ").trim();
        }
        List<String> subjects = uniqueStrings(mustShow, anchors).stream().limit(4).toList();
        String focus = subjects.isEmpty() ? conciseText(request.title(), 90) : String.join(", ", subjects);
        String direction = switch (visualMode) {
            case "algorithm-computational", "review-survey" ->
                    "a refined editorial conceptual image with one clear research metaphor and tangible visual objects";
            case "digital-heritage" ->
                    "a documentary editorial photograph focused on the heritage object, material detail, or reconstruction process";
            case "audio-soundscape" ->
                    "a documentary editorial photograph focused on the sound environment, recording object, or listening activity";
            case "hardware-wearable" ->
                    "a polished product-oriented editorial photograph focused on the actual device and its physical function";
            case "hci-xr" -> hciXrDirection(anchors);
            default ->
                    "a realistic editorial research photograph with one paper-specific subject and a clear application context";
        };
        boolean handsRequired = handsRequired(request, visualMode, mustShow, anchors);
        String interactionPolicy = handsRequired
                ? "The supported hand interaction is essential: show it naturally in a medium or wide composition, with hands secondary to the device and task."
                : "People are optional; avoid visible hands unless they are explicitly required by the evidence.";
        return direction + ". Core evidence-backed subject: " + focus
                + ". Natural composition, visually engaging, restrained academic color palette, no embedded words, no labels, no logo. "
                + interactionPolicy;
    }

    private String hciXrDirection(List<String> anchors) {
        String evidence = String.join(" ", anchors).toLowerCase(Locale.ROOT);
        if (containsAny(evidence, "gaze+pinch", "two-handed 3d", "eye-tracked xr")) {
            return "a documentary XR usability scene with one participant wearing an unbranded eye-tracking headset, both hands naturally manipulating clearly virtual simple 3D geometric objects using pinch gestures; one hand orients the virtual object while the other selects or moves a second virtual part; the simple geometric objects float visibly in front of the participant as virtual content";
        }
        return "a clean editorial photograph focused on the paper-supported XR interface, device, and interaction context";
    }

    private boolean usesEvidenceGroundedPrompt(PublicationImageGenerationRequest request) {
        return request != null
                && request.scenarioPrompt() != null
                && Set.of("EVIDENCE_GROUNDED_VISUAL_BRIEF", "N8N_SHARED_VISUAL_BRIEF").contains(request.scenarioPrompt().promptStrategy())
                && request.positivePrompt() != null
                && request.positivePrompt().length() >= 80;
    }

    private List<String> evidenceGroundedPromptVariants(PublicationImageGenerationRequest request) {
        if (!usesEvidenceGroundedPrompt(request)) return List.of();
        List<String> values = new ArrayList<>();
        values.add(request.positivePrompt().trim());
        String alternative = request.understanding() == null
                ? null : request.understanding().alternativeVisualMetaphor();
        if (alternative != null && !alternative.isBlank() && !alternative.equalsIgnoreCase(values.getFirst())) {
            values.add(alternative.trim());
        }
        return List.copyOf(values.stream().limit(2).toList());
    }

    private String conciseNegativePrompt(PublicationImageGenerationRequest request, List<String> mustAvoid) {
        List<String> values = new ArrayList<>(mustAvoid);
        values.addAll(List.of(
                "text", "letters", "numbers", "logo", "watermark", "caption", "interface gibberish",
                "extra fingers", "malformed hands", "deformed anatomy", "generic laboratory", "stock photo"
        ));
        String xrContext = (safe(request.title()) + " " + safe(request.abstractText())).toLowerCase(Locale.ROOT);
        if (containsAny(xrContext, "gaze-assisted", "bimanual", "gaze+pinch")) {
            values.addAll(List.of("pottery", "ceramic vessel", "paper craft", "physical cards", "craft workshop"));
        }
        String original = conciseText(request.negativePrompt(), 180);
        if (!original.isBlank()) values.add(original);
        return String.join(", ", new LinkedHashSet<>(values));
    }

    private String conciseText(String value, int maxLength) {
        String normalized = safe(value).replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(1, maxLength - 3)).trim() + "...";
    }

    private List<String> evidenceSnippets(PaperEvidencePacket evidence) {
        if (evidence == null) return List.of();
        return uniqueStrings(
                evidence.methodDesignSnippets(),
                evidence.implementationSnippets(),
                evidence.figureCaptions(),
                evidence.evaluationResultSnippets()
        ).stream().limit(10).toList();
    }

    private String classifyVisualMode(PublicationImageGenerationRequest request) {
        return classifyVisualMode(request, null);
    }

    private String classifyVisualMode(PublicationImageGenerationRequest request, JsonNode extracted) {
        FinalPaperUnderstanding understanding = request.understanding();
        String text = String.join(" ", safe(request.title()), safe(request.abstractText()),
                safe(understanding == null ? null : understanding.method()),
                safe(understanding == null ? null : understanding.proposedSystemOrMethod()),
                safe(understanding == null ? null : understanding.targetUsersOrDomain()),
                safe(understanding == null ? null : understanding.visibleInput()),
                safe(understanding == null ? null : understanding.visibleInteraction()),
                extracted == null ? "" : conciseText(extracted.path("content").asText(""), 12000)
        ).toLowerCase(Locale.ROOT);
        if (containsAny(text, "virtual reality", "augmented reality", "mixed reality", "extended reality", "xr interface", "head-mounted display", "gaze-assisted", "eye tracking", "bimanual 3d")) return "hci-xr";
        if (containsAny(text, "hbim", "heritage", "historic building", "museum", "archaeolog")) return "digital-heritage";
        if (containsAny(text, "soundscape", "audio", "acoustic", "sonic")) return "audio-soundscape";
        if (containsAny(text, "wearable", "haptic", "sensor", "hardware device")) return "hardware-wearable";
        if (containsAny(text, "field study", "interview", "ethnograph", "participant observation")) return "field-study";
        if (containsAny(text, "survey paper", "systematic review", "literature review")) return "review-survey";
        if (containsAny(text, "algorithm", "genetic programming", "machine learning", "classification model")) return "algorithm-computational";
        return "system-interface";
    }

    private List<String> strategyEvidenceAnchors(String visualMode, String fullText) {
        String text = safe(fullText).toLowerCase(Locale.ROOT);
        List<String> anchors = new ArrayList<>();
        if ("hci-xr".equals(visualMode)) {
            if (containsAny(text, "gaze+pinch", "gaze and pinch")) anchors.add("Gaze+Pinch selection");
            if (containsAny(text, "bimanual", "two-handed", "both hands")) anchors.add("two-handed 3D object manipulation");
            if (containsAny(text, "eye-tracking", "eye tracking", "gaze-assisted")) anchors.add("eye-tracked XR interaction");
            if (containsAny(text, "hand-tracking", "hand tracking")) anchors.add("hand-tracked interaction");
        } else if ("digital-heritage".equals(visualMode)) {
            if (containsAny(text, "laser scan", "laser scanning")) anchors.add("laser scanning of the heritage object");
            if (text.contains("hbim")) anchors.add("HBIM digital reconstruction");
            if (containsAny(text, "timber", "wooden structure")) anchors.add("historic timber structure");
            if (containsAny(text, "photogrammetry", "point cloud")) anchors.add("photogrammetry point cloud capture");
        } else if ("audio-soundscape".equals(visualMode)) {
            if (containsAny(text, "field recording", "sound recording")) anchors.add("field sound recording");
            if (containsAny(text, "microphone", "recorder")) anchors.add("audio recording equipment");
            if (containsAny(text, "listening", "soundwalk")) anchors.add("situated listening activity");
        } else if ("algorithm-computational".equals(visualMode)) {
            if (containsAny(text, "classification", "risk evaluation")) anchors.add("evidence-grounded classification outcome");
            if (containsAny(text, "ensemble", "stacking")) anchors.add("stacked model combination");
            if (containsAny(text, "interpretable", "explainable")) anchors.add("interpretable model evidence");
        }
        return anchors.stream().distinct().limit(4).toList();
    }

    private boolean handsRequired(PublicationImageGenerationRequest request, String visualMode,
                                  List<String> mustShow, List<String> anchors) {
        if (!List.of("hci-xr", "hardware-wearable").contains(visualMode)) return false;
        FinalPaperUnderstanding understanding = request.understanding();
        String evidence = String.join(" ", safe(request.title()), safe(request.abstractText()),
                String.join(" ", mustShow), String.join(" ", anchors),
                safe(understanding == null ? null : understanding.visibleInput()),
                safe(understanding == null ? null : understanding.visibleInteraction()),
                safe(understanding == null ? null : understanding.mainTaskOrWorkflow())
        ).toLowerCase(Locale.ROOT);
        return containsAny(evidence, "hand", "bimanual", "gesture", "touch", "grasp", "haptic");
    }

    private double sourceCandidatePriority(JsonNode value) {
        double score = value.path("relevanceScore").asDouble(value.path("sourceScore").asDouble(0));
        int page = value.path("pageNumber").asInt(99);
        String caption = value.path("caption").asText("").toLowerCase(Locale.ROOT);
        String type = value.path("imageType").asText("").toLowerCase(Locale.ROOT);
        if (page == 1 && value.path("width").asInt(0) >= 700) score += 0.12;
        if (caption.contains("figure 1") || caption.contains("fig. 1")) score += 0.10;
        if (containsAny(type, "research-photo", "artifact-device", "interface")) score += 0.08;
        if (containsAny(type, "chart", "table", "logo", "decoration")) score -= 0.20;
        return score;
    }

    private List<String> compositionRoles(String visualMode) {
        return switch (visualMode) {
            case "algorithm-computational", "review-survey" -> List.of("method-summary", "editorial-concept");
            case "digital-heritage" -> List.of("artifact-closeup", "study-context");
            case "audio-soundscape", "field-study" -> List.of("artifact-closeup", "study-context");
            case "hardware-wearable" -> List.of("artifact-closeup", "interaction-scene");
            default -> List.of("artifact-closeup", "interaction-scene");
        };
    }

    private String peoplePolicy(String visualMode) {
        return switch (visualMode) {
            case "algorithm-computational", "review-survey" -> "none";
            case "field-study", "audio-soundscape", "hci-xr" -> "optional";
            default -> "optional";
        };
    }

    private String coreMessage(PublicationImageGenerationRequest request) {
        FinalPaperUnderstanding understanding = request.understanding();
        if (understanding == null) return safe(request.abstractText());
        return firstNonBlank(understanding.keyContribution(), understanding.proposedSystemOrMethod(), understanding.abstractSummary());
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private int clampScore(double value) {
        return Math.max(1, Math.min(10, (int) Math.round(value)));
    }

    private long positiveSeed(long seed) {
        if (seed > 0) return seed;
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private String normalizedProviderMode() {
        String value = safe(imageProviderMode).toLowerCase(Locale.ROOT);
        return Set.of("automatic", "qwen", "comfyui", "dual").contains(value) ? value : "dual";
    }

    private String normalizedSourcePolicy() {
        String value = safe(sourcePolicy).toLowerCase(Locale.ROOT);
        return Set.of("balanced", "source-first", "ai-only").contains(value) ? value : "balanced";
    }

    private String normalizedAestheticProfile() {
        String value = safe(aestheticProfile).toLowerCase(Locale.ROOT);
        return value.isBlank() ? "editorial" : value;
    }

    private String normalizedQualityMode() {
        String value = safe(qualityMode).toLowerCase(Locale.ROOT);
        return Set.of("strict", "warn", "off").contains(value) ? value : "strict";
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, effectiveTimeout())))
                .build();
    }

    private URI uri(String path) {
        String base = safe(rendererBaseUrl);
        if (base.isBlank()) base = "http://127.0.0.1:3001";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return URI.create(base + path);
    }

    private long effectiveTimeout() {
        return timeoutSeconds <= 0 ? 600 : timeoutSeconds;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record RenderedCandidate(
            JsonNode image,
            String localUrl,
            String socialCoverUrl,
            String sourceType,
            long seed,
            CandidateSceneScore score,
            List<String> warnings,
            String prompt
    ) {
    }
}
