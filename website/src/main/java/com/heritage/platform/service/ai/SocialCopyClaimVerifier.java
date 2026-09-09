package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.EvidenceSpan;
import com.heritage.platform.dto.ai.SocialCopyClaimVerification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SocialCopyClaimVerifier {
    private static final Pattern STRONG_CLAIM = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?%|首次|第一|最佳|领先|显著|优于|提高|降低|导致|证明|guarantee|first|best|outperform|improve|reduce|cause|prove)");

    public List<SocialCopyClaimVerification> verify(String copy, List<EvidenceSpan> evidence) {
        List<SocialCopyClaimVerification> checks = new ArrayList<>();
        if (copy == null || copy.isBlank()) return checks;
        for (String sentence : copy.split("(?<=[。！？.!?])\\s*")) {
            String claim = sentence.trim();
            if (claim.isBlank() || !STRONG_CLAIM.matcher(claim).find()) continue;
            List<String> support = evidence.stream().filter(span -> sharesTerms(claim, span.text())).map(EvidenceSpan::id).limit(3).toList();
            checks.add(new SocialCopyClaimVerification(claim, support.isEmpty() ? "UNSUPPORTED" : "SUPPORTED", support,
                    support.isEmpty() ? "强主张未找到足够论文证据，发布前需改写或人工确认。" : null));
        }
        return checks;
    }

    private boolean sharesTerms(String claim, String evidence) {
        if (evidence == null) return false;
        String lower = claim.toLowerCase(Locale.ROOT);
        String source = evidence.toLowerCase(Locale.ROOT);
        for (String marker : List.of("首次", "第一", "最佳", "领先", "显著", "优于", "证明", "first", "best", "outperform", "prove")) {
            if (lower.contains(marker) && !source.contains(marker)) {
                return false;
            }
        }
        for (String token : lower.split("[^\\p{L}\\p{N}%]+")) {
            if (token.length() >= 3 && source.contains(token)) return true;
        }
        return false;
    }
}
