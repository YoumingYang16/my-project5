package com.heritage.platform.service.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextEvidenceServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void extractsEveryNativeTextPageWhenNoCoverageLimitIsConfigured() throws Exception {
        Path pdf = tempDir.resolve("three-pages.pdf");
        try (PDDocument document = new PDDocument()) {
            for (int pageNumber = 1; pageNumber <= 3; pageNumber++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText("Evidence from page " + pageNumber + ".");
                    content.endText();
                }
            }
            document.save(pdf.toFile());
        }

        PdfTextEvidenceService service = new PdfTextEvidenceService();
        ReflectionTestUtils.setField(service, "maxPages", 0);
        ReflectionTestUtils.setField(service, "maxChars", 0);

        var evidence = service.extract(pdf);

        assertThat(evidence.available()).isTrue();
        assertThat(evidence.pageCount()).isEqualTo(3);
        assertThat(evidence.extractedPageCount()).isEqualTo(3);
        assertThat(evidence.fullPageCoverage()).isTrue();
        assertThat(evidence.pages()).extracting(page -> page.text())
                .contains("Evidence from page 1.", "Evidence from page 2.", "Evidence from page 3.");
    }
}
