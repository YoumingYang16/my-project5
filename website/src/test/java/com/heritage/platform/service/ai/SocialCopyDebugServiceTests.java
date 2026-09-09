package com.heritage.platform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.dto.ai.SocialCopyProviderAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocialCopyDebugServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesProviderAttemptsAlongsideBoundedStageDebugJsonFiles() throws Exception {
        SocialCopyDebugService service = new SocialCopyDebugService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "uploadDirectory", temporaryDirectory.toString());
        OpenAiSocialCopyService.GeneratedSocialCopy generated = new OpenAiSocialCopyService.GeneratedSocialCopy(
                Map.of("engaging", "grounded copy", "concise", "grounded copy", "academic", "grounded copy"),
                List.of("Research", "Heritage", "Interaction")
        );

        SocialCopyProviderAttempt openAiFailure = new SocialCopyProviderAttempt(
                "OPENAI", true, true, false, "RATE_LIMITED", 429, true, 25L, "rate limited"
        );
        SocialCopyProviderAttempt deepSeekSuccess = new SocialCopyProviderAttempt(
                "DEEPSEEK", true, true, true, null, null, false, 40L, null
        );
        service.write(
                42L, generated, "DEEPSEEK", "zh", "engaging", List.of("manual review advised"),
                List.of(openAiFailure, deepSeekSuccess), openAiFailure, true, true
        );

        Path debug = temporaryDirectory.resolve("generated-covers/42/debug");
        assertThat(Files.list(debug).map(path -> path.getFileName().toString()).toList()).containsExactlyInAnyOrder(
                "social-copy-evidence-digest.json",
                "social-copy-angle.json",
                "social-copy-hook-candidates.json",
                "social-copy-selected-hook.json",
                "social-copy-draft-variants.json",
                "social-copy-quality-review.json",
                "social-copy-provider-attempts.json",
                "social-copy-openai-error.json",
                "social-copy-deepseek-result.json",
                "social-copy-final-result.json"
        );
        assertThat(Files.readString(debug.resolve("social-copy-final-result.json")))
                .contains("DEEPSEEK", "grounded copy")
                .doesNotContain("api-key", "JWT");
        assertThat(Files.readString(debug.resolve("social-copy-provider-attempts.json")))
                .contains("RATE_LIMITED", "429", "DEEPSEEK")
                .doesNotContain("secret", "api-key");
    }
}
