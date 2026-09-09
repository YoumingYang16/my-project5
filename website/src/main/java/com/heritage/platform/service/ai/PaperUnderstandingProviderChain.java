package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.PaperUnderstandingProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class PaperUnderstandingProviderChain {

    private static final Logger logger = LoggerFactory.getLogger(PaperUnderstandingProviderChain.class);

    private final Map<String, PaperUnderstandingProvider> providers;
    private final PaperUnderstandingSemanticValidator semanticValidator;

    @Value("${ai-understanding.provider-priority:n8n,deepseek,evidence_fallback}")
    private String providerPriority = "n8n,deepseek,evidence_fallback";

    public PaperUnderstandingProviderChain(
            List<PaperUnderstandingProvider> providers,
            PaperUnderstandingSemanticValidator semanticValidator
    ) {
        Map<String, PaperUnderstandingProvider> byName = new LinkedHashMap<>();
        for (PaperUnderstandingProvider provider : providers) {
            String key = switch (provider.providerName().toUpperCase()) {
                case "N8N" -> "n8n";
                case "OPENAI" -> "openai";
                case "DEEPSEEK" -> "deepseek";
                case "EVIDENCE_FALLBACK" -> "evidence_fallback";
                default -> null;
            };
            if (key != null) byName.put(key, provider);
        }
        this.providers = Map.copyOf(byName);
        this.semanticValidator = semanticValidator;
    }

    public PaperUnderstandingProviderResult resolve(PaperEvidencePacket evidencePacket) {
        List<String> warnings = new ArrayList<>();
        String openAiFailure = null;
        List<String> order = configuredPriority();
        logger.info("Paper understanding provider priority: {}", order);

        for (String providerKey : order) {
            PaperUnderstandingProvider provider = providers.get(providerKey);
            if (provider == null) {
                warnings.add("Unknown paper understanding provider was skipped: " + providerKey + ".");
                continue;
            }
            boolean enabled = provider.isEnabled();
            logger.info(
                    "Paper understanding provider considered: provider={}, enabled={}, model={}",
                    provider.providerName(), enabled, provider.modelName()
            );
            if (!enabled) {
                if ("n8n".equals(providerKey)) {
                    openAiFailure = "n8n/DeepSeek is disabled or not configured.";
                }
                continue;
            }
            boolean available;
            try {
                available = provider.isAvailable();
            } catch (RuntimeException ex) {
                available = false;
            }
            if (!available) {
                String reason = provider.providerName() + " is unavailable.";
                logger.warn("Paper understanding provider unavailable: provider={}, model={}", provider.providerName(), provider.modelName());
                if ("n8n".equals(providerKey)) {
                    openAiFailure = reason;
                }
                continue;
            }
            try {
                logger.info("Paper understanding provider started: provider={}, model={}", provider.providerName(), provider.modelName());
                OpenAiPaperUnderstanding raw = provider.understand(evidencePacket);
                OpenAiPaperUnderstanding validated = !"EVIDENCE_FALLBACK".equalsIgnoreCase(provider.providerName())
                        ? semanticValidator.validate(raw, evidencePacket, provider.providerName())
                        : raw;
                warnings.addAll(validated.warnings());
                if ("EVIDENCE_FALLBACK".equalsIgnoreCase(provider.providerName())) {
                    warnings.add(DeterministicPaperUnderstandingProvider.FALLBACK_WARNING);
                }
                logger.info(
                        "Paper understanding provider succeeded: provider={}, model={}, confidence={}",
                        provider.providerName(), provider.modelName(), validated.confidenceLevel()
                );
                return new PaperUnderstandingProviderResult(
                        validated,
                        provider.providerName(),
                        provider.modelName(),
                        openAiFailure,
                        distinct(warnings)
                );
            } catch (Exception ex) {
                String reason = provider.providerName() + " failed: " + AiCoverDiagnostics.safeExceptionSummary(ex);
                logger.warn(
                        "Paper understanding provider failed: provider={}, model={}, failure={}",
                        provider.providerName(), provider.modelName(), AiCoverDiagnostics.safeExceptionSummary(ex)
                );
                if ("n8n".equals(providerKey)) {
                    openAiFailure = AiCoverDiagnostics.sanitize(ex.getMessage());
                }
            }
        }
        String detail = openAiFailure == null || openAiFailure.isBlank()
                ? "n8n/DeepSeek did not produce usable structured output."
                : openAiFailure;
        throw new AiCoverWorkflowException(
                PaperAiQualityValidator.AUTHORITATIVE_UNDERSTANDING_MESSAGE + " " + detail
        );
    }

    public List<String> priority() {
        return configuredPriority();
    }

    private List<String> configuredPriority() {
        List<String> values = java.util.Arrays.stream(providerPriority.split(","))
                .map(String::trim).map(String::toLowerCase).filter(value -> !value.isBlank())
                .filter(value -> List.of("n8n", "deepseek", "evidence_fallback").contains(value))
                .distinct().toList();
        return values.isEmpty() ? List.of("n8n", "deepseek", "evidence_fallback") : values;
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values == null ? List.of() : values));
    }
}


