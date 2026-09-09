package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.PaperEvidencePacket;
import com.heritage.platform.dto.ai.EvidenceSpan;
import com.heritage.platform.dto.ai.SocialCopyEvidenceDigest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SocialCopyEvidenceDigestService {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "test", "test1", "sample", "placeholder", "unknown", "untitled", "n/a", "null",
            "none", "not specified", "not clearly specified", "paper", "article", "content"
    );

    public SocialCopyEvidenceDigest build(
            FinalPaperUnderstanding understanding,
            PaperEvidencePacket evidence
    ) {
        List<String> warnings = new ArrayList<>();
        String retrievedEvidence = evidence == null ? null : evidenceText(1200,
                evidence.evidenceSpans().stream().map(EvidenceSpan::text).toArray(String[]::new));
        String title = firstUseful(
                evidence == null ? null : evidence.title(),
                understanding == null ? null : understanding.title()
        );
        if (title == null) {
            title = "Title not reliably available";
            warnings.add("A reliable paper title was not available; the copy should avoid relying on the title.");
        }

        String venue = firstUseful(
                evidence == null ? null : evidence.venue(),
                understanding == null ? null : understanding.venue()
        );
        Integer year = evidence != null && evidence.year() != null
                ? evidence.year() : understanding == null ? null : understanding.year();
        String venueAndYear = joinVenueAndYear(venue, year);
        if (venueAndYear.isBlank()) {
            venueAndYear = "Venue and year not reliably available";
            warnings.add("Venue or year evidence was incomplete.");
        }

        List<String> topicEvidence = mergeLists(
                evidence == null ? List.of() : evidence.subjectTerms(),
                evidence == null ? List.of() : evidence.keywords(),
                evidence == null ? List.of() : evidence.domainTerms()
        );
        String domain = firstUseful(
                joinLimited(topicEvidence, 5, 220),
                understanding == null ? null : understanding.targetUsersOrDomain()
        );
        if (domain == null) {
            domain = "the research area supported by the extracted evidence";
            warnings.add("Domain evidence was limited.");
        }

        String problem = evidenceText(650,
                understanding == null ? null : understanding.researchProblem(),
                first(evidence == null ? List.of() : evidence.problemContextSnippets()),
                retrievedEvidence,
                understanding == null ? null : understanding.abstractSummary(),
                evidence == null ? null : evidence.abstractText()
        );
        String system = evidenceText(650,
                understanding == null ? null : understanding.proposedSystemOrMethod(),
                understanding == null ? null : understanding.method(),
                first(evidence == null ? List.of() : evidence.methodDesignSnippets()),
                retrievedEvidence,
                first(evidence == null ? List.of() : evidence.implementationSnippets())
        );
        String usage = evidenceText(700,
                understanding == null ? null : understanding.likelyApplicationScenario(),
                understanding == null ? null : understanding.applicationEnvironment(),
                understanding == null ? null : understanding.mainTaskOrWorkflow(),
                understanding == null ? null : understanding.visibleInteraction(),
                first(evidence == null ? List.of() : evidence.applicationClues()),
                retrievedEvidence,
                first(evidence == null ? List.of() : evidence.figureCaptions())
        );
        String value = evidenceText(650,
                understanding == null ? null : understanding.keyContribution(),
                understanding == null ? null : understanding.whyItMatters(),
                understanding == null ? null : understanding.expectedOutcome(),
                first(evidence == null ? List.of() : evidence.conclusionSnippets()),
                retrievedEvidence,
                first(evidence == null ? List.of() : evidence.evaluationResultSnippets())
        );
        String audience = evidenceText(300,
                understanding == null ? null : understanding.targetUsersOrDomain(),
                domain
        );

        if (problem == null) {
            problem = "The available evidence identifies a practical research problem, but its precise boundary needs checking against the paper.";
            warnings.add("Problem evidence was incomplete.");
        }
        if (system == null) {
            system = "The paper presents an evidence-supported system or method whose precise name needs checking against the paper.";
            warnings.add("The proposed system or method was not clearly named in the extracted evidence.");
        }
        if (usage == null) {
            usage = "The available evidence supports a practical use workflow, while detailed interaction steps remain uncertain.";
            warnings.add("Usage evidence was incomplete.");
        }
        if (value == null) {
            value = "The work offers an evidence-supported way to address the identified problem without supporting stronger outcome claims.";
            warnings.add("Value evidence was incomplete.");
        }
        if (audience == null) {
            audience = "researchers and practitioners in the evidence-supported topic area";
            warnings.add("Target-reader evidence was incomplete.");
        }

        List<String> keywords = meaningfulList(mergeLists(
                evidence == null ? List.of() : evidence.keywords(),
                evidence == null ? List.of() : evidence.subjectTerms(),
                evidence == null ? List.of() : evidence.domainTerms()
        ), 16);
        List<String> hashtags = hashtagCandidates(keywords, title);
        List<String> sources = meaningfulList(evidence == null ? List.of() : evidence.evidenceSources(), 10);
        if (sources.isEmpty()) {
            sources = List.of("FINAL_PAPER_UNDERSTANDING");
        }
        if (evidence != null) {
            warnings.addAll(evidence.warnings());
        }
        String confidence = normalizeConfidence(understanding == null ? null : understanding.confidenceLevel());

        return new SocialCopyEvidenceDigest(
                title, venueAndYear, domain, problem, system, usage, value, audience,
                keywords, hashtags, sources, confidence, distinct(warnings, 20)
        );
    }

    public boolean isUsable(SocialCopyEvidenceDigest digest) {
        return digest != null
                && useful(digest.problemEvidence())
                && useful(digest.proposedSystemEvidence())
                && useful(digest.usageEvidence())
                && useful(digest.valueEvidence());
    }

    private String evidenceText(int limit, String... values) {
        List<String> useful = new ArrayList<>();
        for (String value : values) {
            String cleaned = clean(value);
            if (useful(cleaned) && !containsEllipsis(cleaned) && !useful.contains(cleaned)) {
                useful.add(cleaned);
            }
            if (useful.size() >= 3) {
                break;
            }
        }
        return useful.isEmpty() ? null : clip(String.join(" | ", useful), limit);
    }

    private List<String> hashtagCandidates(List<String> keywords, String title) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String candidate : keywords) {
            String value = candidate.replaceAll("[^\\p{L}\\p{N}_-]", "").trim();
            if (value.length() >= 2 && value.length() <= 30 && useful(value)) {
                result.add(value);
            }
            if (result.size() >= 8) {
                break;
            }
        }
        if (result.size() < 3 && useful(title)) {
            for (String token : title.split("[^\\p{L}\\p{N}]+")) {
                if (token.length() >= 4 && token.length() <= 24 && useful(token)) {
                    result.add(token);
                }
                if (result.size() >= 5) {
                    break;
                }
            }
        }
        return result.stream().limit(8).toList();
    }

    @SafeVarargs
    private final List<String> mergeLists(List<String>... lists) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (List<String> list : lists) {
            if (list != null) {
                list.stream().map(this::clean).filter(this::useful).forEach(values::add);
            }
        }
        return List.copyOf(values);
    }

    private List<String> meaningfulList(List<String> values, int limit) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::clean).filter(this::useful).distinct().limit(limit).toList();
    }

    private List<String> distinct(List<String> values, int limit) {
        return values.stream().map(this::clean).filter(this::useful).distinct().limit(limit).toList();
    }

    private String joinLimited(List<String> values, int count, int maxChars) {
        String joined = String.join(", ", meaningfulList(values, count));
        return joined.isBlank() ? null : clip(joined, maxChars);
    }

    private String first(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(this::clean).filter(this::useful).findFirst().orElse(null);
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (useful(value)) {
                return clean(value);
            }
        }
        return null;
    }

    private String joinVenueAndYear(String venue, Integer year) {
        if (useful(venue) && year != null) {
            return clean(venue) + " · " + year;
        }
        if (useful(venue)) {
            return clean(venue);
        }
        return year == null ? "" : String.valueOf(year);
    }

    private String normalizeConfidence(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return Set.of("HIGH", "MEDIUM", "LOW").contains(normalized) ? normalized : "LOW";
    }

    private boolean useful(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return false;
        }
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        return !PLACEHOLDERS.contains(normalized)
                && !normalized.matches("^(?:test\\d*|sample|placeholder|unknown|untitled|null|n/?a)(?:[ _-]*(?:paper|title|article|content))?$");
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean containsEllipsis(String value) {
        return value.contains("…") || value.matches("(?s).*\\.{3,}.*");
    }

    private String clip(String value, int limit) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.length() <= limit) {
            return cleaned;
        }
        String prefix = cleaned.substring(0, limit);
        int boundary = Math.max(
                Math.max(prefix.lastIndexOf('。'), prefix.lastIndexOf('！')),
                Math.max(prefix.lastIndexOf('.'), prefix.lastIndexOf('!'))
        );
        if (boundary >= limit / 2) {
            return prefix.substring(0, boundary + 1).trim();
        }
        int space = prefix.lastIndexOf(' ');
        return (space >= limit / 2 ? prefix.substring(0, space) : prefix).trim();
    }
}
