package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.ReferenceGroundingContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class PaperReferenceGroundingServiceTests {

    private final PaperReferenceGroundingService service = enabledService();

    @Test
    void extractsCaptionsSectionsKeywordsAndSalientTermsWithoutDomainDictionary() {
        String tei = """
                <TEI xmlns="http://www.tei-c.org/ns/1.0">
                  <teiHeader><profileDesc><textClass><keywords><term>tactile control</term></keywords></textClass></profileDesc></teiHeader>
                  <text><body>
                    <div><head>Field evaluation</head><p>The adaptive controller uses tactile control during object manipulation. Tactile control improves completion.</p></div>
                    <figure><head>System in use</head><figDesc>The platform manipulates an object while sensing contact.</figDesc></figure>
                    <figure type="table"><head>Completion results</head></figure>
                  </body></text>
                </TEI>
                """;

        ReferenceGroundingContext context = service.extract(tei);

        assertThat(context.figureCaptions())
                .contains("System in use The platform manipulates an object while sensing contact.");
        assertThat(context.tableCaptions()).contains("Completion results");
        assertThat(context.sectionHints()).anyMatch(value -> value.startsWith("Field evaluation"));
        assertThat(context.interfaceOrSystemMentions()).contains("tactile control", "tactile", "control");
        assertThat(context.taskMentions()).anyMatch(value -> value.startsWith("Field evaluation"));
        assertThat(context.environmentMentions()).isEmpty();
        assertThat(context.deviceMentions()).isEmpty();
        assertThat(context.userGroupMentions()).isEmpty();
    }

    @Test
    void malformedTeiReturnsWarningsInsteadOfThrowing() {
        ReferenceGroundingContext context = service.extract("<TEI><broken>");

        assertThat(context.extractedVisualClues()).isEmpty();
        assertThat(context.groundingWarnings()).isNotEmpty();
    }

    private PaperReferenceGroundingService enabledService() {
        PaperReferenceGroundingService result = new PaperReferenceGroundingService();
        ReflectionTestUtils.setField(result, "enabled", true);
        return result;
    }
}
