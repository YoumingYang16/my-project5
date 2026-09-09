package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.OpenAiPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekPaperUnderstandingService implements PaperUnderstandingProvider {
    private final DeepSeekStructuredResponseClient client;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.paper-understanding.max-content-chars:24000}")
    private int maxContentChars;

    public DeepSeekPaperUnderstandingService(DeepSeekStructuredResponseClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override public String providerName() { return "DEEPSEEK"; }
    @Override public String modelName() { return "deepseek-paper-understanding"; }
    @Override public boolean isEnabled() { return client.isEnabled(); }
    @Override public boolean isAvailable() { return client.isEnabled(); }

    @Override
    public OpenAiPaperUnderstanding understand(PaperEvidencePacket evidencePacket) {
        if (evidencePacket == null || (evidencePacket.compactEvidenceText().isBlank() && evidencePacket.evidenceSpans().isEmpty())) {
            throw new AiCoverWorkflowException("DeepSeek paper understanding failed: no paper evidence was available.");
        }
        String systemPrompt = """
                Analyze academic papers for evidence-grounded image generation. Return one JSON object only.
                Every visible object, device, person, action, and setting must be supported by the evidence.
                Prefer specific physical nouns and interactions over title fragments or generic technology words.
                Never invent equipment, environments, users, results, or brands. Put uncertainty in warnings.
                Create two distinct, production-ready English image prompts. The first must emphasize the exact
                research object, device, material, interface, heritage object, or other paper-specific artifact.
                The second must emphasize the supported study environment, activity, interaction, or application.
                Each prompt must describe one coherent 3:2 editorial image, use no more than three evidence-backed
                anchors, prohibit readable text and logos, and avoid generic laboratory or laptop stock scenes.
                Never use split scenes, split screens, diptychs, collages, before-and-after layouts, or multiple panels.
                A screen may appear only when supported by evidence, and its content must be blank, abstract, blurred,
                or unreadable; must_show_elements must never require readable words, labels, equations, or interface text.
                Mention visible hands only when the evidenced research action cannot be represented without them.
                Otherwise use object close-ups, side views, rear views, or wider context and omit hand-related language.
                Before returning JSON, check that every requested visual element is traceable to supplied evidence and
                that prompt, alternativePrompt, must_show_elements, and must_avoid_elements do not contradict each other.
                """;
        String userPrompt = """
                Build a precise structured understanding for a publication cover. Identify the actual research object,
                input devices, participant actions, environment, and output. If hands, gaze, controllers, wearables,
                or body interaction are central, state that explicitly.
                Required keys: title, abstract_summary, authors, year, venue, research_problem,
                target_users_or_domain, method, proposed_system_or_method, key_implementation, key_contribution,
                important_system_components, input_output_relationship, why_it_matters, likely_application_scenario,
                application_environment, main_task_or_workflow, visualizable_entities, visualizable_interactions,
                visualizable_environment, visible_interface_or_device, visible_input, visible_output,
                visible_interaction, expected_outcome, possible_visual_metaphor, alternative_visual_metaphor,
                must_show_elements, must_avoid_elements, forbidden_visual_elements, warnings, confidence_level.
                Arrays must be JSON arrays. confidence_level is HIGH, MEDIUM, or LOW.
                possible_visual_metaphor and alternative_visual_metaphor must each be detailed English image prompts
                of 70 to 120 words, grounded only in the evidence, and must use visibly different composition roles.
                must_show_elements must contain at least three concrete paper-specific visual anchors when supported.
                Both image prompts must depict different single-scene compositions. Do not encode layout instructions
                that conflict with text-free output, and move any uncertain or unsupported visual detail to warnings.

                CURATED PAPER EVIDENCE:
                """ + evidencePacket.compactEvidenceText()
                + "\n\nFIGURE CAPTIONS:\n" + String.join("\n", evidencePacket.figureCaptions())
                + "\n\nFULL PDF TEXT (primary source; ignore references when selecting visible content):\n"
                + paperContent(evidencePacket);
        String json = client.requestJson(systemPrompt, userPrompt, "paper_understanding", permissiveSchema());
        try {
            return new PaperUnderstandingJsonCodec(objectMapper).parse(json, true);
        } catch (Exception ex) {
            throw new AiCoverWorkflowException("DeepSeek paper understanding returned invalid JSON: "
                    + AiCoverDiagnostics.safeExceptionSummary(ex));
        }
    }

    String paperContent(PaperEvidencePacket evidencePacket) {
        List<com.heritage.platform.dto.ai.EvidenceSpan> pdfPages = evidencePacket.evidenceSpans().stream()
                .filter(span -> "PDF_TEXT".equalsIgnoreCase(span.source()))
                .filter(span -> span.text() != null && !span.text().isBlank())
                .sorted(Comparator.comparing(
                        com.heritage.platform.dto.ai.EvidenceSpan::pageNumber,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
        StringBuilder content = new StringBuilder();
        for (com.heritage.platform.dto.ai.EvidenceSpan page : pdfPages) {
            if (content.length() > 0) content.append("\n\n");
            if (page.pageNumber() != null) {
                content.append("[PDF page ").append(page.pageNumber()).append("]\n");
            }
            content.append(page.text().trim());
        }
        if (content.length() == 0) content.append(evidencePacket.compactEvidenceText());
        int limit = maxContentChars <= 0 ? 24000 : maxContentChars;
        if (content.length() <= limit) return content.toString();
        int headLength = Math.max(1, limit * 4 / 5);
        int tailLength = Math.max(1, limit - headLength - 32);
        return content.substring(0, headLength)
                + "\n\n[PDF text truncated]\n\n"
                + content.substring(content.length() - tailLength);
    }
    private Map<String, Object> permissiveSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", true);
        return schema;
    }
}
