package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.heritage.platform.dto.ai.AiCoverCandidate;
import com.heritage.platform.dto.ai.CandidateManifest;
import com.heritage.platform.dto.ai.StyledImagePrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class ComfyUiImageGenerationService {

    private static final String CANDIDATE_MANIFEST = "candidate-manifest.json";
    private static final String RENDERER_MANIFEST = "renderer-manifest.json";
    private static final int MANIFEST_VERSION = 2;
    public static final String PROMPT_VERSION = "application-scene-product-in-use-v3-doi-evidence";
    private static final Pattern CANDIDATE_PATTERN = Pattern.compile(
            "publication-(\\d+)-candidate-(\\d+)-seed-(\\d+)\\.(png|jpg|jpeg|webp)",
            Pattern.CASE_INSENSITIVE
    );

    @Value("${comfyui.enabled:true}")
    private boolean comfyUiEnabled;

    @Value("${comfyui.base-url:http://127.0.0.1:8188}")
    private String baseUrl;

    @Value("${comfyui.workflow:sdxl-basic-teaser-workflow.json}")
    private String workflow;

    @Value("${comfyui.checkpoint:sd_xl_base_1.0.safetensors}")
    private String checkpoint;

    @Value("${comfyui.internal-candidates:${comfyui.candidates:1}}")
    private int internalCandidateCount;

    @Value("${comfyui.return-candidates:1}")
    private int returnCandidateCount;

    @Value("${comfyui.timeout-seconds:180}")
    private long timeoutSeconds;

    private final ObjectMapper objectMapper;
    private final UploadPathService uploadPathService;

    public ComfyUiImageGenerationService(ObjectMapper objectMapper, UploadPathService uploadPathService) {
        this.objectMapper = objectMapper;
        this.uploadPathService = uploadPathService;
    }

    public List<AiCoverCandidate> generateCandidates(
            Long publicationId,
            StyledImagePrompt prompt,
            String positivePrompt,
            String negativePrompt
    ) {
        if (!comfyUiEnabled) {
            throw new AiCoverWorkflowException("ComfyUI is disabled.");
        }
        String workflowTemplate = loadWorkflowTemplate();
        int count = effectiveInternalCandidateCount();
        List<AiCoverCandidate> candidates = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            long seed = Math.abs(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
            String candidateId = "candidate-" + index;
            String outputPrefix = "publication-" + publicationId + "-" + candidateId + "-seed-" + seed;
            CandidateVariation variation = candidateVariation(index);
            String candidatePrompt = candidatePositivePrompt(positivePrompt, index);
            String imageUrl = generateSingleCandidate(
                    publicationId,
                    index,
                    seed,
                    outputPrefix,
                    workflowTemplate,
                    prompt.recommendedWidth(),
                    prompt.recommendedHeight(),
                    candidatePrompt,
                    negativePrompt
            );
            candidates.add(new AiCoverCandidate(
                    candidateId,
                    imageUrl,
                    seed,
                    false,
                    candidatePrompt,
                    null,
                    variation.explanation(),
                    List.of()
            ));
        }
        return candidates;
    }

    public List<AiCoverCandidate> listExistingCandidates(
            Long publicationId,
            String pdfHash,
            String understandingHash
    ) {
        Path directory = uploadPathService.existingGeneratedCoverDirectory(publicationId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        Path manifest = directory.resolve(CANDIDATE_MANIFEST);
        if (Files.isRegularFile(manifest)) {
            try {
                CandidateManifest value = objectMapper.readValue(manifest.toFile(), CandidateManifest.class);
                if (!manifestMatches(value, publicationId, pdfHash, understandingHash)) {
                    return List.of();
                }
                return value.candidates().stream()
                        .filter(candidate -> candidate != null && candidate.imageUrl() != null)
                        .filter(candidate -> candidateExists(publicationId, candidate))
                        .limit(effectiveReturnCandidateCount())
                        .toList();
            } catch (IOException ex) {
                return List.of();
            }
        }
        return List.of();
    }

    public List<AiCoverCandidate> listExistingCandidates(Long publicationId, String pdfHash) {
        Path directory = uploadPathService.existingGeneratedCoverDirectory(publicationId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        Path manifest = directory.resolve(CANDIDATE_MANIFEST);
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        try {
            CandidateManifest value = objectMapper.readValue(manifest.toFile(), CandidateManifest.class);
            if (!manifestMatchesForPdf(value, publicationId, pdfHash)) {
                return List.of();
            }
            return value.candidates().stream()
                    .filter(candidate -> candidate != null && candidate.imageUrl() != null)
                    .filter(candidate -> candidateExists(publicationId, candidate))
                    .limit(effectiveReturnCandidateCount())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public void saveCandidateManifest(
            Long publicationId,
            List<AiCoverCandidate> candidates,
            String generatedFromProvider,
            String understandingHash,
            String pdfHash
    ) {
        try {
            Path directory = uploadPathService.generatedCoverDirectory(publicationId);
            Path manifest = directory.resolve(CANDIDATE_MANIFEST).normalize();
            if (!manifest.startsWith(directory)) {
                throw new AiCoverWorkflowException("AI cover candidate manifest path is invalid.");
            }
            List<AiCoverCandidate> safeCandidates = candidates == null ? List.of() : List.copyOf(candidates);
            CandidateManifest value = new CandidateManifest(
                    MANIFEST_VERSION,
                    "OPENAI".equalsIgnoreCase(generatedFromProvider) ? "OPENAI" : generatedFromProvider,
                    understandingHash,
                    publicationId,
                    pdfHash,
                    Instant.now().toString(),
                    PROMPT_VERSION,
                    safeCandidates.stream().map(AiCoverCandidate::imageUrl).toList(),
                    safeCandidates
            );
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), value);
        } catch (AiCoverWorkflowException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AiCoverWorkflowException("AI cover candidate details could not be saved.", ex);
        }
    }

    public void clearIfStale(
            Long publicationId,
            String pdfHash,
            String understandingHash,
            String preservedCoverUrl
    ) {
        Path directory = uploadPathService.existingGeneratedCoverDirectory(publicationId);
        Path manifest = directory.resolve(CANDIDATE_MANIFEST);
        if (!Files.isRegularFile(manifest)) {
            clearCandidates(publicationId, preservedCoverUrl);
            return;
        }
        try {
            CandidateManifest value = objectMapper.readValue(manifest.toFile(), CandidateManifest.class);
            if (!manifestMatches(value, publicationId, pdfHash, understandingHash)) {
                clearCandidates(publicationId, preservedCoverUrl);
            }
        } catch (IOException ex) {
            clearCandidates(publicationId, preservedCoverUrl);
        }
    }

    public void clearCandidates(Long publicationId, String preservedCoverUrl) {
        Path directory = uploadPathService.existingGeneratedCoverDirectory(publicationId);
        if (!Files.isDirectory(directory)) {
            return;
        }
        Path preserved = null;
        if (preservedCoverUrl != null && !preservedCoverUrl.isBlank()) {
            try {
                preserved = uploadPathService.resolveSelectedGeneratedCover(publicationId, preservedCoverUrl);
            } catch (RuntimeException ignored) {
                // A non-generated cover does not need preservation in this directory.
            }
        }
        try (Stream<Path> files = Files.list(directory)) {
            Path finalPreserved = preserved;
            files.filter(Files::isRegularFile).forEach(path -> {
                boolean manifest = CANDIDATE_MANIFEST.equals(path.getFileName().toString())
                        || RENDERER_MANIFEST.equals(path.getFileName().toString());
                boolean generatedCandidate = CANDIDATE_PATTERN.matcher(path.getFileName().toString()).matches();
                if ((manifest || generatedCandidate) && (finalPreserved == null || !path.equals(finalPreserved))) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new AiCoverWorkflowException("Stale AI cover candidates could not be cleared.", ex);
                    }
                }
            });
        } catch (IOException ex) {
            throw new AiCoverWorkflowException("Stale AI cover candidates could not be cleared.", ex);
        }
    }

    private boolean manifestMatches(
            CandidateManifest value,
            Long publicationId,
            String pdfHash,
            String understandingHash
    ) {
        return value != null
                && value.manifestVersion() == MANIFEST_VERSION
                && supportedProvider(value.generatedFromProvider())
                && publicationId != null && publicationId.equals(value.publicationId())
                && pdfHash != null && pdfHash.equals(value.pdfHash())
                && understandingHash != null && understandingHash.equals(value.generatedFromUnderstandingHash())
                && PROMPT_VERSION.equals(value.promptVersion())
                && value.candidates() != null && !value.candidates().isEmpty();
    }

    private boolean manifestMatchesForPdf(CandidateManifest value, Long publicationId, String pdfHash) {
        return value != null
                && value.manifestVersion() == MANIFEST_VERSION
                && supportedProvider(value.generatedFromProvider())
                && publicationId != null && publicationId.equals(value.publicationId())
                && pdfHash != null && pdfHash.equals(value.pdfHash())
                && value.generatedFromUnderstandingHash() != null
                && !value.generatedFromUnderstandingHash().isBlank()
                && PROMPT_VERSION.equals(value.promptVersion())
                && value.candidates() != null && !value.candidates().isEmpty();
    }

    private boolean supportedProvider(String value) {
        return "OPENAI".equalsIgnoreCase(value) || "EVIDENCE_FALLBACK".equalsIgnoreCase(value);
    }

    int effectiveInternalCandidateCount() {
        return internalCandidateCount <= 0 ? 1 : internalCandidateCount;
    }

    int effectiveReturnCandidateCount() {
        return returnCandidateCount <= 0 ? 1 : returnCandidateCount;
    }

    String candidatePositivePrompt(String basePrompt, int candidateIndex) {
        CandidateVariation variation = candidateVariation(candidateIndex);
        String groundedPrompt = basePrompt == null ? "" : basePrompt.trim();
        return groundedPrompt + (groundedPrompt.isEmpty() ? "" : ", ") + variation.promptSuffix();
    }

    private CandidateVariation candidateVariation(int candidateIndex) {
        int variationIndex = Math.floorMod(candidateIndex - 1, 3);
        return switch (variationIndex) {
            case 1 -> new CandidateVariation(
                    "candidate 2, interaction and workflow close-up: make the main stakeholder-contribution interaction, interface, device, model artifact, tool operation, input, and visible output especially clear; preserve the paper-supported environment",
                    "Interaction and workflow close-up."
            );
            case 2 -> new CandidateVariation(
                    "candidate 3, outcome and context scene: show the broader paper-supported domain environment, the proposed contribution still clearly visible in use, and the immediate benefit or outcome caused by it",
                    "Outcome and context application scene."
            );
            default -> new CandidateVariation(
                    "candidate 1, full application scene: show the proposed system, method, product, tool, model, or artifact being used in its intended real-world or plausible environment, with stakeholder, contribution, task, and response all visible",
                    "Full paper-grounded application scene."
            );
        };
    }

    private record CandidateVariation(String promptSuffix, String explanation) {
    }

    private boolean candidateExists(Long publicationId, AiCoverCandidate candidate) {
        try {
            uploadPathService.resolveSelectedGeneratedCover(publicationId, candidate.imageUrl());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String generateSingleCandidate(
            Long publicationId,
            int candidateIndex,
            long seed,
            String outputPrefix,
            String workflowTemplate,
            Integer width,
            Integer height,
            String positivePrompt,
            String negativePrompt
    ) {
        try {
            JsonNode workflowJson = buildApiWorkflow(
                    workflowTemplate,
                    positivePrompt,
                    negativePrompt,
                    seed,
                    width,
                    height,
                    outputPrefix
            );
            String promptId = submitPrompt(workflowJson);
            ComfyImageReference generatedImage = waitForImage(promptId);
            byte[] imageBytes = downloadImage(generatedImage);

            Path outputDir = uploadPathService.generatedCoverDirectory(publicationId);
            Path target = outputDir.resolve(outputPrefix + ".png").normalize();
            if (!target.startsWith(outputDir)) {
                throw new AiCoverWorkflowException("Generated cover output path is invalid.");
            }
            Files.write(target, imageBytes);
            return uploadPathService.toUploadUrl(target);
        } catch (AiCoverWorkflowException ex) {
            throw ex;
        } catch (ConnectException ex) {
            throw new AiCoverWorkflowException(
                    "ComfyUI is unavailable. Please check COMFYUI_BASE_URL and make sure ComfyUI is running.",
                    ex
            );
        } catch (IOException ex) {
            throw new AiCoverWorkflowException("ComfyUI image candidate " + candidateIndex + " failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiCoverWorkflowException("Image generation timed out. Please try again.", ex);
        }
    }

    private String submitPrompt(JsonNode workflowJson) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("client_id", UUID.randomUUID().toString());
        requestBody.put("prompt", workflowJson);

        HttpRequest request = HttpRequest.newBuilder(uri("/prompt"))
                .timeout(Duration.ofSeconds(effectiveTimeout()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw comfyUiSubmissionException(response.statusCode(), response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String promptId = root.path("prompt_id").asText(null);
        if (promptId == null || promptId.isBlank()) {
            if (root.has("error") || root.has("node_errors")) {
                throw new AiCoverWorkflowException(
                        "SDXL checkpoint is missing or workflow validation failed. Please check COMFYUI_CHECKPOINT and the ComfyUI workflow."
                );
            }
            throw new AiCoverWorkflowException("ComfyUI did not return a prompt ID.");
        }
        return promptId;
    }

    private ComfyImageReference waitForImage(String promptId) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + effectiveTimeout() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            HttpRequest request = HttpRequest.newBuilder(uri("/history/" + promptId))
                    .timeout(Duration.ofSeconds(Math.min(20, effectiveTimeout())))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                HistoryPollResult result = parseHistory(promptId, response.body());
                if (result.image() != null) {
                    return result.image();
                }
                if (result.completed()) {
                    throw new AiCoverWorkflowException("ComfyUI completed but no output image was found.");
                }
            }
            Thread.sleep(2000);
        }
        throw new AiCoverWorkflowException("Image generation timed out. Please try again.");
    }

    private HistoryPollResult parseHistory(String promptId, String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode history = root.has(promptId) ? root.path(promptId) : root;
        JsonNode outputs = history.path("outputs");
        if (!outputs.isObject()) {
            return new HistoryPollResult(null, isCompleted(history));
        }
        var fields = outputs.fields();
        while (fields.hasNext()) {
            JsonNode nodeOutput = fields.next().getValue();
            JsonNode images = nodeOutput.path("images");
            if (!images.isArray() || images.isEmpty()) {
                continue;
            }
            JsonNode image = images.get(0);
            String filename = image.path("filename").asText(null);
            if (filename == null || filename.isBlank()) {
                continue;
            }
            return new HistoryPollResult(new ComfyImageReference(
                    filename,
                    image.path("subfolder").asText(""),
                    image.path("type").asText("output")
            ), true);
        }
        return new HistoryPollResult(null, isCompleted(history));
    }

    private byte[] downloadImage(ComfyImageReference image) throws IOException, InterruptedException {
        URI viewUri = UriComponentsBuilder.fromUri(uri("/view"))
                .queryParam("filename", image.filename())
                .queryParam("subfolder", image.subfolder() == null ? "" : image.subfolder())
                .queryParam("type", image.type() == null ? "output" : image.type())
                .build()
                .encode()
                .toUri();
        HttpRequest request = HttpRequest.newBuilder(viewUri)
                .timeout(Duration.ofSeconds(effectiveTimeout()))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length == 0) {
            throw new AiCoverWorkflowException("ComfyUI did not return generated image data.");
        }
        return response.body();
    }

    JsonNode buildApiWorkflow(
            String template,
            String positivePrompt,
            String negativePrompt,
            long seed,
            Integer width,
            Integer height,
            String outputPrefix
    ) throws IOException {
        String replaced = template
                .replace("{{POSITIVE_PROMPT}}", jsonStringContent(positivePrompt))
                .replace("{{NEGATIVE_PROMPT}}", jsonStringContent(negativePrompt))
                .replace("{{SEED}}", String.valueOf(seed))
                .replace("{{WIDTH}}", String.valueOf(effectiveWidth(width)))
                .replace("{{HEIGHT}}", String.valueOf(effectiveHeight(height)))
                .replace("{{OUTPUT_PREFIX}}", jsonStringContent(outputPrefix))
                .replace("{{CHECKPOINT}}", jsonStringContent(effectiveCheckpoint()));
        JsonNode root = objectMapper.readTree(replaced);
        ObjectNode apiWorkflow = root.has("nodes") && root.path("nodes").isArray()
                ? convertUiWorkflowToApi(root)
                : root.deepCopy();
        configureApiWorkflow(apiWorkflow, positivePrompt, negativePrompt, seed, width, height, outputPrefix);
        return apiWorkflow;
    }

    private ObjectNode convertUiWorkflowToApi(JsonNode uiWorkflow) {
        ObjectNode apiWorkflow = objectMapper.createObjectNode();
        Map<Integer, LinkReference> links = new LinkedHashMap<>();
        for (JsonNode link : uiWorkflow.path("links")) {
            if (link.isArray() && link.size() >= 6) {
                links.put(link.get(0).asInt(), new LinkReference(link.get(1).asInt(), link.get(2).asInt()));
            }
        }

        for (JsonNode node : uiWorkflow.path("nodes")) {
            String nodeId = node.path("id").asText();
            String classType = node.path("type").asText();
            if (nodeId.isBlank() || classType.isBlank()) {
                continue;
            }
            ObjectNode apiNode = objectMapper.createObjectNode();
            apiNode.put("class_type", classType);
            ObjectNode inputs = objectMapper.createObjectNode();
            addWidgetInputs(classType, node.path("widgets_values"), inputs);
            for (JsonNode input : node.path("inputs")) {
                if (!input.hasNonNull("link")) {
                    continue;
                }
                LinkReference link = links.get(input.path("link").asInt());
                if (link != null) {
                    inputs.set(input.path("name").asText(), objectMapper.valueToTree(List.of(
                            String.valueOf(link.sourceNodeId()),
                            link.sourceSlot()
                    )));
                }
            }
            apiNode.set("inputs", inputs);
            apiWorkflow.set(nodeId, apiNode);
        }
        return apiWorkflow;
    }

    private void addWidgetInputs(String classType, JsonNode widgets, ObjectNode inputs) {
        if ("CheckpointLoaderSimple".equals(classType)) {
            inputs.put("ckpt_name", textWidget(widgets, 0, effectiveCheckpoint()));
            return;
        }
        if ("CLIPTextEncode".equals(classType)) {
            inputs.put("text", textWidget(widgets, 0, ""));
            return;
        }
        if ("EmptyLatentImage".equals(classType)) {
            inputs.put("width", intWidget(widgets, 0, 1024));
            inputs.put("height", intWidget(widgets, 1, 768));
            inputs.put("batch_size", intWidget(widgets, 2, 1));
            return;
        }
        if ("KSampler".equals(classType)) {
            inputs.put("seed", longWidget(widgets, 0, 1L));
            inputs.put("steps", intWidget(widgets, 2, 28));
            inputs.put("cfg", doubleWidget(widgets, 3, 6.5));
            inputs.put("sampler_name", textWidget(widgets, 4, "euler"));
            inputs.put("scheduler", textWidget(widgets, 5, "simple"));
            inputs.put("denoise", doubleWidget(widgets, 6, 1.0));
            return;
        }
        if ("SaveImage".equals(classType)) {
            inputs.put("filename_prefix", textWidget(widgets, 0, "ComfyUI"));
        }
    }

    private void configureApiWorkflow(
            ObjectNode apiWorkflow,
            String positivePrompt,
            String negativePrompt,
            long seed,
            Integer width,
            Integer height,
            String outputPrefix
    ) {
        PromptNodeIds promptNodes = findPromptNodeIds(apiWorkflow);
        var fields = apiWorkflow.fields();
        int clipTextFallbackIndex = 0;
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!(entry.getValue() instanceof ObjectNode node)) {
                continue;
            }
            String classType = node.path("class_type").asText();
            ObjectNode inputs = node.withObject("/inputs");
            if ("CheckpointLoaderSimple".equals(classType)) {
                inputs.put("ckpt_name", effectiveCheckpoint());
            } else if ("EmptyLatentImage".equals(classType)) {
                inputs.put("width", effectiveWidth(width));
                inputs.put("height", effectiveHeight(height));
                if (!inputs.has("batch_size")) {
                    inputs.put("batch_size", 1);
                }
            } else if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType)) {
                inputs.put("seed", seed);
            } else if ("SaveImage".equals(classType)) {
                inputs.put("filename_prefix", outputPrefix);
            } else if ("CLIPTextEncode".equals(classType)) {
                if (promptNodes.positiveNodeIds().contains(entry.getKey())) {
                    inputs.put("text", positivePrompt);
                } else if (promptNodes.negativeNodeIds().contains(entry.getKey())) {
                    inputs.put("text", negativePrompt);
                } else if (clipTextFallbackIndex == 0) {
                    inputs.put("text", positivePrompt);
                    clipTextFallbackIndex++;
                } else {
                    inputs.put("text", negativePrompt);
                    clipTextFallbackIndex++;
                }
            }
        }
    }

    private PromptNodeIds findPromptNodeIds(ObjectNode apiWorkflow) {
        List<String> positiveNodeIds = new ArrayList<>();
        List<String> negativeNodeIds = new ArrayList<>();
        var fields = apiWorkflow.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode node = entry.getValue();
            String classType = node.path("class_type").asText();
            if (!"KSampler".equals(classType) && !"KSamplerAdvanced".equals(classType)) {
                continue;
            }
            addLinkedNodeId(node.path("inputs").path("positive"), positiveNodeIds);
            addLinkedNodeId(node.path("inputs").path("negative"), negativeNodeIds);
        }
        return new PromptNodeIds(positiveNodeIds, negativeNodeIds);
    }

    private void addLinkedNodeId(JsonNode value, List<String> nodeIds) {
        if (value.isArray() && value.size() >= 1) {
            String nodeId = value.get(0).asText();
            if (!nodeId.isBlank() && !nodeIds.contains(nodeId)) {
                nodeIds.add(nodeId);
            }
        }
    }

    private boolean isCompleted(JsonNode history) {
        JsonNode status = history.path("status");
        return status.path("completed").asBoolean(false)
                || "success".equalsIgnoreCase(status.path("status_str").asText(""))
                || "completed".equalsIgnoreCase(status.path("status_str").asText(""));
    }

    private AiCoverWorkflowException comfyUiSubmissionException(int statusCode, String responseBody) {
        String body = responseBody == null ? "" : responseBody;
        if (statusCode == 400 || body.contains("Validation") || body.contains("node_errors")
                || body.contains("ckpt_name") || body.contains("CheckpointLoaderSimple")) {
            return new AiCoverWorkflowException(
                    "SDXL checkpoint is missing or workflow validation failed. Please check COMFYUI_CHECKPOINT and the ComfyUI workflow."
            );
        }
        return new AiCoverWorkflowException("ComfyUI prompt submission failed with HTTP " + statusCode + ".");
    }

    private int effectiveWidth(Integer width) {
        return width == null || width <= 0 ? 1024 : width;
    }

    private int effectiveHeight(Integer height) {
        return height == null || height <= 0 ? 768 : height;
    }

    private String effectiveCheckpoint() {
        return checkpoint == null || checkpoint.isBlank() ? "sd_xl_base_1.0.safetensors" : checkpoint.trim();
    }

    private String textWidget(JsonNode widgets, int index, String fallback) {
        JsonNode value = widgets.path(index);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }

    private int intWidget(JsonNode widgets, int index, int fallback) {
        JsonNode value = widgets.path(index);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private long longWidget(JsonNode widgets, int index, long fallback) {
        JsonNode value = widgets.path(index);
        return value.isNumber() ? value.asLong() : fallback;
    }

    private double doubleWidget(JsonNode widgets, int index, double fallback) {
        JsonNode value = widgets.path(index);
        return value.isNumber() ? value.asDouble() : fallback;
    }

    private String jsonStringContent(String value) {
        try {
            String json = objectMapper.writeValueAsString(value == null ? "" : value);
            return json.substring(1, json.length() - 1);
        } catch (IOException ex) {
            return "";
        }
    }

    private String loadWorkflowTemplate() {
        String configured = workflow == null || workflow.isBlank() ? "sdxl-basic-teaser-workflow.json" : workflow.trim();
        Path path = Path.of(configured);
        try {
            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            ClassPathResource resource = new ClassPathResource("comfyui/workflows/" + configured);
            if (!resource.exists()) {
                throw new AiCoverWorkflowException("ComfyUI workflow template not found.");
            }
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (AiCoverWorkflowException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AiCoverWorkflowException("ComfyUI workflow template not found.", ex);
        }
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, effectiveTimeout())))
                .build();
    }

    private URI uri(String path) {
        String cleanedBase = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:8188" : baseUrl.trim();
        if (cleanedBase.endsWith("/")) {
            cleanedBase = cleanedBase.substring(0, cleanedBase.length() - 1);
        }
        return URI.create(cleanedBase + path);
    }

    private long effectiveTimeout() {
        return timeoutSeconds <= 0 ? 180 : timeoutSeconds;
    }

    private record ComfyImageReference(String filename, String subfolder, String type) {
    }

    private record HistoryPollResult(ComfyImageReference image, boolean completed) {
    }

    private record LinkReference(int sourceNodeId, int sourceSlot) {
    }

    private record PromptNodeIds(List<String> positiveNodeIds, List<String> negativeNodeIds) {
    }
}
