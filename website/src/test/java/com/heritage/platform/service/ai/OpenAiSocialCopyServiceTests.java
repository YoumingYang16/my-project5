package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiSocialCopyServiceTests {

    @Test
    void compactModeReturnsTheFullPipelineInOneProviderCall() {
        OpenAiStructuredResponseClient legacyClient = mock(OpenAiStructuredResponseClient.class);
        SocialCopyStructuredJsonClient compactClient = mock(SocialCopyStructuredJsonClient.class);
        when(compactClient.providerName()).thenReturn("OPENAI");
        when(compactClient.requestJson(anyString(), anyString(), eq("social_copy_compact_pipeline"), any()))
                .thenReturn(compactResponse());
        OpenAiSocialCopyService service = new OpenAiSocialCopyService(
                new ObjectMapper(), legacyClient, new PaperAiQualityValidator()
        );

        OpenAiSocialCopyService.GeneratedSocialCopy result = service.generateCompact(
                understanding(), evidence(), "en", compactClient
        );

        assertThat(result.artifacts()).isNotNull();
        assertThat(result.artifacts().hookCandidates()).hasSize(3);
        assertThat(result.artifacts().finalValidation().valid()).isTrue();
        assertThat(result.variants()).containsKeys("engaging", "concise", "professional", "academic");
        verify(compactClient, times(1)).requestJson(
                anyString(), anyString(), eq("social_copy_compact_pipeline"), any()
        );
    }

    @Test
    void runsMultiStagePipelineAndReturnsDebugArtifacts() {
        OpenAiStructuredResponseClient client = mock(OpenAiStructuredResponseClient.class);
        when(client.requestJson(anyString(), anyString(), any(), eq(List.of())))
                .thenAnswer(invocation -> stagedResponse(invocation.getArgument(1)));
        OpenAiSocialCopyService service = new OpenAiSocialCopyService(
                new ObjectMapper(), client, new PaperAiQualityValidator()
        );

        OpenAiSocialCopyService.GeneratedSocialCopy result = service.generate(understanding(), evidence(), "en");

        assertThat(result.variants()).containsKeys("engaging", "concise", "professional", "academic");
        assertThat(result.artifacts()).isNotNull();
        assertThat(result.artifacts().evidenceDigest().paperTitle()).isEqualTo("Inspection assistant");
        assertThat(result.artifacts().hookCandidates()).hasSize(3);
        assertThat(result.artifacts().selectedHook().selectedHook().hookText()).contains("inspectors");
        assertThat(result.artifacts().qualityReview().overallScore()).isEqualTo(5);
        assertThat(result.artifacts().finalValidation().valid()).isTrue();
        verify(client).requestJson(anyString(), eq("social_copy_angle"), any(), eq(List.of()));
        verify(client).requestJson(anyString(), eq("social_copy_hook_candidates"), any(), eq(List.of()));
        verify(client).requestJson(anyString(), eq("social_copy_hook_selection"), any(), eq(List.of()));
        verify(client).requestJson(anyString(), eq("social_copy_draft_variants"), any(), eq(List.of()));
        verify(client).requestJson(anyString(), eq("social_copy_quality_review"), any(), eq(List.of()));
    }

    @Test
    void parsesGroundedVariantsAndTopicHashtags() {
        OpenAiStructuredResponseClient client = mock(OpenAiStructuredResponseClient.class);
        when(client.requestJson(anyString(), anyString(), any(), eq(List.of())))
                .thenAnswer(invocation -> stagedResponse(invocation.getArgument(1)));
        OpenAiSocialCopyService service = new OpenAiSocialCopyService(
                new ObjectMapper(), client, new PaperAiQualityValidator()
        );

        OpenAiSocialCopyService.GeneratedSocialCopy result = service.generate(understanding(), evidence(), "en");

        assertThat(result.variants()).containsKeys("engaging", "concise", "professional", "academic");
        assertThat(result.variants().values()).allSatisfy(copy -> assertThat(copy)
                .containsIgnoringCase("inspection assistant")
                .doesNotContain("test1"));
        assertThat(result.hashtags()).containsExactly("IndustrialInspection", "HumanComputerInteraction", "QualityControl");
    }

    @Test
    void rejectsPlaceholderLikeOpenAiCopyInsteadOfReturningFiller() {
        OpenAiStructuredResponseClient client = mock(OpenAiStructuredResponseClient.class);
        String invalid = validSocialCopy().replace("sensor-guided", "test1 sensor-guided");
        when(client.requestJson(anyString(), anyString(), any(), eq(List.of())))
                .thenAnswer(invocation -> stagedResponseWithFinalCopy(invocation.getArgument(1), invalid));
        OpenAiSocialCopyService service = new OpenAiSocialCopyService(
                new ObjectMapper(), client, new PaperAiQualityValidator()
        );

        assertThatThrownBy(() -> service.generate(understanding(), evidence(), "en"))
                .isInstanceOf(AiCoverWorkflowException.class)
                .hasMessageContaining("failed final validation")
                .hasMessageContaining("Placeholder");
    }

    @Test
    void rejectsEllipsizedOpenAiCopySoTheGroundedFallbackCanCompleteIt() {
        OpenAiStructuredResponseClient client = mock(OpenAiStructuredResponseClient.class);
        String invalid = validSocialCopy() + "...";
        when(client.requestJson(anyString(), anyString(), any(), eq(List.of())))
                .thenAnswer(invocation -> stagedResponseWithFinalCopy(invocation.getArgument(1), invalid));
        OpenAiSocialCopyService service = new OpenAiSocialCopyService(
                new ObjectMapper(), client, new PaperAiQualityValidator()
        );

        assertThatThrownBy(() -> service.generate(understanding(), evidence(), "en"))
                .isInstanceOf(AiCoverWorkflowException.class)
                .hasMessageContaining("failed final validation")
                .hasMessageContaining("Ellipses");
    }

    private String stagedResponse(String schemaName) {
        return switch (schemaName) {
            case "social_copy_angle" -> """
                    {"painPoint":"Inspectors can miss defects during repetitive review.",
                     "openingHookIntent":"Start from the pressure of repetitive inspection.",
                     "proposedSystem":"sensor-guided inspection assistant",
                     "usageScenario":"an industrial inspection station",
                     "howItWorks":"sensor observations become visible findings for inspector review",
                     "mostInterestingPoint":"it connects sensing to a concrete human review workflow",
                     "whyValuable":"it supports a more consistent review process",
                     "targetReaders":["industrial inspectors","HCI researchers"],
                     "topicAreas":["industrial inspection","human-computer interaction"],
                     "hashtags":["IndustrialInspection","HumanComputerInteraction","QualityControl"],
                     "confidenceLevel":"HIGH","warnings":[]}
                    """;
            case "social_copy_hook_candidates" -> """
                    {"candidates":[
                      {"hookText":"Have inspectors ever reached the end of a repetitive review and wondered whether a defect slipped past?","hookType":"question","whyItWorks":"It starts from a familiar inspection concern.","groundingEvidence":"Manual inspection can be inconsistent."},
                      {"hookText":"At a busy inspection station, turning every sensor observation into a useful finding is harder than it sounds.","hookType":"scenario","whyItWorks":"It names the real work setting.","groundingEvidence":"The assistant is used at an inspection station."},
                      {"hookText":"More sensor data does not automatically make an inspection easier; the useful part is knowing what to review.","hookType":"contrast","whyItWorks":"It creates a grounded contrast.","groundingEvidence":"Observations are turned into visible findings."}
                    ]}
                    """;
            case "social_copy_hook_selection" -> """
                    {"evaluations":[
                      {"candidateIndex":0,"relatability":5,"specificity":5,"attractiveness":4,"groundingInEvidence":5,"fitForXiaohongshu":4,"notAbstractLike":5,"reason":"Strongest grounded reader connection."},
                      {"candidateIndex":1,"relatability":4,"specificity":5,"attractiveness":4,"groundingInEvidence":5,"fitForXiaohongshu":4,"notAbstractLike":5,"reason":"Concrete scenario."},
                      {"candidateIndex":2,"relatability":4,"specificity":4,"attractiveness":4,"groundingInEvidence":5,"fitForXiaohongshu":4,"notAbstractLike":5,"reason":"Useful contrast."}
                     ],"selectedCandidateIndex":0,"rationale":"The first hook is relatable and specific."}
                    """;
            case "social_copy_draft_variants" -> variantsResponse(validSocialCopy());
            case "social_copy_quality_review" -> """
                    {"hookAttractiveness":5,"groundingInPaperEvidence":5,"specificity":5,"positiveTone":5,
                     "xiaohongshuReadability":5,"notAbstractLike":5,"structureCompleteness":5,
                     "noHallucination":5,"noPlaceholderText":5,"noEllipses":5,"overallScore":5,
                     "problems":[],"suggestedImprovements":[],"shouldRewrite":false}
                    """;
            default -> throw new AssertionError("Unexpected schema: " + schemaName);
        };
    }

    private String stagedResponseWithFinalCopy(String schemaName, String copy) {
        return switch (schemaName) {
            case "social_copy_draft_variants", "social_copy_polished_rewrite", "social_copy_targeted_repair" ->
                    variantsResponse(copy);
            default -> stagedResponse(schemaName);
        };
    }

    private String variantsResponse(String copy) {
        return """
                {"engaging":%s,"concise":%s,"professional":%s,
                 "hashtags":["IndustrialInspection","HumanComputerInteraction","QualityControl"]}
                """.formatted(json(copy), json(copy), json(copy));
    }

    private String validSocialCopy() {
        return "📎 Paper share\n\n🤔 Have inspectors ever finished a repetitive review and wondered whether a defect slipped past?"
                + "\n\n🧩 This paper presents a sensor-guided inspection assistant for that concrete problem."
                + "\n\n🛠️ Inspectors use it at an inspection station, where sensor observations become visible findings for review."
                + "\n\n✅ Why it matters: the design connects sensing to a practical human review workflow without claiming unsupported results."
                + "\n\n👀 Worth reading for industrial inspectors, quality-control practitioners, and HCI researchers."
                + "\n\n#IndustrialInspection #HumanComputerInteraction #QualityControl";
    }

    private String compactResponse() {
        String copy = json(validSocialCopy());
        return """
                {
                  "angle":{
                    "painPoint":"Inspectors can miss defects during repetitive review.",
                    "openingHookIntent":"Start from the pressure of repetitive inspection.",
                    "proposedSystem":"sensor-guided inspection assistant",
                    "usageScenario":"an industrial inspection station",
                    "howItWorks":"sensor observations become visible findings for review",
                    "mostInterestingPoint":"it connects sensing to a human review workflow",
                    "whyValuable":"it supports a more consistent review process",
                    "targetReaders":["industrial inspectors"],
                    "topicAreas":["industrial inspection"],
                    "hashtags":["IndustrialInspection","HumanComputerInteraction","QualityControl"],
                    "confidenceLevel":"HIGH","warnings":[]
                  },
                  "hookCandidates":[
                    {"hookText":"Have inspectors ever wondered whether a defect slipped past during repetitive review?","hookType":"question","whyItWorks":"It is relatable.","groundingEvidence":"Manual review can be inconsistent."},
                    {"hookText":"At a busy inspection station, useful findings matter more than another stream of raw observations.","hookType":"scenario","whyItWorks":"It names the setting.","groundingEvidence":"The system is used at an inspection station."},
                    {"hookText":"More sensor data does not automatically make inspection easier; the key is knowing what to review.","hookType":"contrast","whyItWorks":"It creates contrast.","groundingEvidence":"Observations become visible findings."}
                  ],
                  "selectedHook":{
                    "evaluations":[
                      {"candidateIndex":0,"relatability":5,"specificity":5,"attractiveness":5,"groundingInEvidence":5,"fitForXiaohongshu":4,"notAbstractLike":5,"reason":"Strongest hook."},
                      {"candidateIndex":1,"relatability":4,"specificity":5,"attractiveness":4,"groundingInEvidence":5,"fitForXiaohongshu":4,"notAbstractLike":5,"reason":"Concrete setting."},
                      {"candidateIndex":2,"relatability":4,"specificity":4,"attractiveness":4,"groundingInEvidence":5,"fitForXiaohongshu":4,"notAbstractLike":5,"reason":"Grounded contrast."}
                    ],
                    "selectedCandidateIndex":0,
                    "rationale":"The first hook is strongest."
                  },
                  "variants":{"engaging":%s,"concise":%s,"professional":%s},
                  "qualityReview":{
                    "hookAttractiveness":5,"groundingInPaperEvidence":5,"specificity":5,"positiveTone":5,
                    "xiaohongshuReadability":5,"notAbstractLike":5,"structureCompleteness":5,
                    "noHallucination":5,"noPlaceholderText":5,"noEllipses":5,"overallScore":5,
                    "problems":[],"suggestedImprovements":[],"shouldRewrite":false
                  },
                  "finalCopy":%s,
                  "hashtags":["IndustrialInspection","HumanComputerInteraction","QualityControl"],
                  "warnings":[]
                }
                """.formatted(copy, copy, copy, copy);
    }

    private String json(String value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private FinalPaperUnderstanding understanding() {
        return new FinalPaperUnderstanding(
                "Inspection assistant", "An assistant supports industrial inspection.", List.of(), 2026,
                "manual inspection can be inconsistent", "industrial inspectors",
                "sensor observations guide review", "sensor and interface", "consistent review",
                List.of("inspection assistant", "sensor module"), "observations become findings",
                "supports inspectors", null, List.of("generic poster"), "inspector reviews a part",
                List.of("inspector", "part"), List.of("reviews highlighted findings"), "inspection station",
                "HIGH", null, "sensor-guided inspection assistant", "inspection interface",
                "sensor observations", "highlighted findings", "consistent review",
                List.of("inspector", "assistant", "station"), List.of("generic poster"),
                "inspection station", "inspect a part", "review highlighted findings"
        );
    }

    private PaperEvidencePacket evidence() {
        String body = "The paper proposes a sensor-guided inspection assistant for industrial quality review. ".repeat(5);
        return new PaperEvidencePacket(
                "Inspection assistant", body, List.of(), 2026, null, null, List.of("inspection"),
                List.of(body), List.of(body), List.of(body), List.of(body), List.of(body), List.of(), List.of(),
                List.of("inspection"), List.of(body), List.of("inspection interface"), body, body.length(), List.of()
        );
    }
}
