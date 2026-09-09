package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.SocialCopyEvidenceDigest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocialCopyEvidenceDigestServiceTests {

    private final SocialCopyEvidenceDigestService service = new SocialCopyEvidenceDigestService();

    @Test
    void ignoresPlaceholderTitleAndCarriesDoiEnrichedContextIntoDigest() {
        PaperEvidencePacket evidence = new PaperEvidencePacket(
                "AR Heritage Guide", "A mobile AR guide supports on-site heritage interpretation.",
                List.of("A. Author"), 2026, "Heritage Science", "10.1000/example",
                List.of("Augmented Reality", "Cultural Heritage"),
                List.of("Visitors struggle to connect surviving remains with earlier spatial layouts."),
                List.of("The paper presents an AR heritage guide."),
                List.of("Visitors use a mobile interface at the heritage site."), List.of(),
                List.of("The guide supports interpretation of spatial change."),
                List.of("A visitor uses the guide beside a heritage structure."), List.of(),
                List.of("Digital Heritage"), List.of("on-site mobile AR"), List.of("mobile interface"),
                "compact evidence", 16, List.of(), "Example Publisher", "https://doi.org/10.1000/example",
                List.of("Heritage Conservation", "Augmented Reality"), "citation metadata",
                List.of("GROBID", "DOI", "PDF_TEXT", "PUBLICATION_METADATA")
        );

        SocialCopyEvidenceDigest digest = service.build(null, evidence);

        assertThat(digest.paperTitle()).isEqualTo("AR Heritage Guide");
        assertThat(digest.venueAndYear()).contains("Heritage Science", "2026");
        assertThat(digest.keywords()).contains("Heritage Conservation", "Augmented Reality");
        assertThat(digest.hashtagCandidates()).contains("AugmentedReality");
        assertThat(digest.evidenceSources()).containsExactly(
                "GROBID", "DOI", "PDF_TEXT", "PUBLICATION_METADATA"
        );
        assertThat(service.isUsable(digest)).isTrue();
    }
}
