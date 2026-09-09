package com.heritage.platform.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCoverDiagnosticsTests {

    @Test
    void removesSecretsFromDiagnosticMessages() {
        String sanitized = AiCoverDiagnostics.sanitize(
                "Authorization: Bearer abc.def.ghi key=sk-test_secret_123456789 data:application/pdf;base64,QUJDRA=="
        );

        assertThat(sanitized)
                .doesNotContain("abc.def.ghi")
                .doesNotContain("sk-test_secret_123456789")
                .doesNotContain("QUJDRA==")
                .contains("[redacted-api-key]", "Bearer [redacted]", "[redacted-data-url]");
    }
}
