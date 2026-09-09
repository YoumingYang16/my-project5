package com.heritage.platform.service.ai;

import com.heritage.platform.dto.ai.FinalPaperUnderstanding;
import com.heritage.platform.dto.ai.VisualBriefPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VisualBriefPlannerService {

    public VisualBriefPlan plan(FinalPaperUnderstanding understanding) {
        String mainSubject = firstUseful(
                understanding.proposedSystemOrMethod(),
                understanding.keyImplementation(),
                understanding.method(),
                understanding.keyContribution(),
                understanding.title(),
                "paper contribution"
        );
        String contribution = firstUseful(
                understanding.keyContribution(),
                understanding.whyItMatters(),
                understanding.abstractSummary(),
                "the paper's main contribution"
        );
        String metaphor = firstUseful(
                understanding.possibleVisualMetaphor(),
                "a focused research prototype scene that shows inputs becoming a useful result"
        );

        List<String> components = new ArrayList<>(safeList(understanding.importantSystemComponents()));
        if (components.isEmpty()) {
            components.add("source material");
            components.add("core method");
            components.add("interpretable result");
        }

        return new VisualBriefPlan(
                mainSubject,
                mainSubject,
                contribution,
                metaphor,
                components.stream().limit(3).toList(),
                List.of("subtle paper texture", "only context and environment supported by the paper"),
                components.stream().skip(3).limit(3).toList(),
                mergedAvoidList(mergeAvoid(understanding.mustAvoidElements(), understanding.forbiddenVisualElements())),
                "wide 3:2 card composition, one clear central subject, limited supporting elements, no dense labels"
        );
    }

    private List<String> mergedAvoidList(List<String> forbidden) {
        List<String> avoid = new ArrayList<>();
        avoid.add("boring flowchart");
        avoid.add("generic abstract AI wallpaper");
        avoid.add("cluttered diagram");
        avoid.add("unreadable text");
        avoid.add("fake logos");
        for (String item : safeList(forbidden)) {
            if (!avoid.contains(item)) {
                avoid.add(item);
            }
        }
        return avoid;
    }

    private List<String> mergeAvoid(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(safeList(first));
        for (String value : safeList(second)) {
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"not clearly specified".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "paper contribution";
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
