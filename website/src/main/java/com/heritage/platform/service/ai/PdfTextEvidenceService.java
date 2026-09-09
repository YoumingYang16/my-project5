package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.PdfTextEvidence;
import com.heritage.platform.dto.ai.PdfPageEvidence;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

@Service
public class PdfTextEvidenceService {

    private static final Logger logger = LoggerFactory.getLogger(PdfTextEvidenceService.class);

    @Value("${ai-cover.pdf-text-max-pages:0}")
    private int maxPages;

    @Value("${ai-cover.pdf-text-max-chars:0}")
    private int maxChars;

    public PdfTextEvidence extract(Path pdfPath) {
        if (pdfPath == null || !Files.isRegularFile(pdfPath)) {
            return PdfTextEvidence.unavailable("Uploaded PDF text was unavailable.");
        }
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                return PdfTextEvidence.unavailable("Uploaded PDF contained no readable pages.");
            }
            PDFTextStripper firstPageStripper = new PDFTextStripper();
            firstPageStripper.setStartPage(1);
            firstPageStripper.setEndPage(1);
            String firstPage = clean(firstPageStripper.getText(document));

            List<PdfPageEvidence> pages = new ArrayList<>();
            StringBuilder documentText = new StringBuilder();
            int readablePages = maxPages <= 0 ? pageCount : Math.min(pageCount, maxPages);
            for (int page = 1; page <= readablePages; page++) {
                PDFTextStripper pageStripper = new PDFTextStripper();
                pageStripper.setStartPage(page);
                pageStripper.setEndPage(page);
                String pageText = clean(pageStripper.getText(document));
                if (pageText != null && !pageText.isBlank()) {
                    pages.add(new PdfPageEvidence(page, pageText, 1.0d));
                    if (documentText.length() > 0) {
                        documentText.append("\n\n");
                    }
                    documentText.append(pageText);
                }
            }
            String text = truncate(documentText.toString(), maxChars);
            boolean available = text != null && text.length() >= 40;
            List<String> warnings = new ArrayList<>();
            if (readablePages < pageCount) {
                warnings.add("PDF extraction was limited to " + readablePages + " of " + pageCount + " pages by configuration.");
            }
            if (pages.size() < readablePages) {
                warnings.add("PDF text was recovered from " + pages.size() + " of " + readablePages + " requested pages; scanned or unreadable pages may need OCR.");
            }
            if (!available) {
                warnings.add("Uploaded PDF did not contain enough machine-readable text.");
            }
            logger.info(
                    "PDF text evidence extraction completed: pageCount={}, pagesRequested={}, pagesExtracted={}, firstPageCharacters={}, documentCharacters={}, available={}",
                    pageCount, readablePages, pages.size(), length(firstPage), length(text), available
            );
            return new PdfTextEvidence(
                    firstPage,
                    text,
                    available,
                    warnings,
                    pages,
                    pageCount,
                    pages.size()
            );
        } catch (Exception ex) {
            logger.warn("PDF text evidence extraction failed: {}", AiCoverDiagnostics.safeExceptionSummary(ex));
            return PdfTextEvidence.unavailable("Uploaded PDF text extraction failed; other evidence sources were retained.");
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceAll("(?<=\\p{L})-\\s+(?=\\p{Ll})", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\s*\\R\\s*", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return limit <= 0 || value.length() <= limit ? value : value.substring(0, limit).trim();
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
