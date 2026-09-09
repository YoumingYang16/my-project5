package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.EvidenceSpan;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.SocialCopyContentPlan;
import com.heritage.platform.dto.ai.SocialCopyContentSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lightweight per-paper retrieval. A vector database is intentionally unnecessary for one uploaded paper. */
@Service
public class EvidenceRetrievalService {
    public List<EvidenceSpan> retrieve(PaperEvidencePacket packet, SocialCopyContentSpec spec, SocialCopyContentPlan plan) {
        List<EvidenceSpan> spans = packet == null ? List.of() : packet.evidenceSpans();
        if (spans.isEmpty()) return List.of();
        Set<String> query = tokens(String.join(" ", plan.mustCover()) + " " + plan.audienceGuidance() + " " + plan.goalGuidance()
                + " " + spec.audience() + " " + spec.goal());
        List<EvidenceSpan> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingDouble((EvidenceSpan span) -> score(span, query)).reversed()
                .thenComparing(span -> span.pageNumber() == null ? Integer.MAX_VALUE : span.pageNumber()));
        return sorted.stream().limit(8).toList();
    }

    private double score(EvidenceSpan span, Set<String> query) {
        Set<String> words = tokens(span.text());
        long overlap = words.stream().filter(query::contains).count();
        double sectionBonus = switch (span.section() == null ? "" : span.section()) {
            case "evaluation", "conclusion", "method", "implementation" -> 1.0d;
            default -> 0.0d;
        };
        return overlap + sectionBonus + (span.pageNumber() != null ? 0.2d : 0d);
    }

    private Set<String> tokens(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value == null) return result;
        for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2) result.add(token);
        }
        return result;
    }
}
