package com.heritage.platform.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ApplicationSceneBrief(
        String paperSceneType,
        String targetUser,
        String userRole,
        String environment,
        String technologyOrProduct,
        String technologyFormFactor,
        String mainTask,
        String taskContext,
        String supportAction,
        String expectedOutcome,
        String sceneMoment,
        List<String> visibleObjects,
        List<String> visibleInteractions,
        List<String> domainSpecificDetails,
        List<String> mustShowElements,
        List<String> mustNotShowElements,
        String realismNotes,
        String compositionHint,
        String cameraView,
        String styleDirection,
        String confidenceLevel,
        List<String> warnings,
        String sourceProvider
) {
    public ApplicationSceneBrief(
            String paperSceneType,
            String targetUser,
            String userRole,
            String environment,
            String technologyOrProduct,
            String technologyFormFactor,
            String mainTask,
            String taskContext,
            String supportAction,
            String expectedOutcome,
            String sceneMoment,
            List<String> visibleObjects,
            List<String> visibleInteractions,
            List<String> domainSpecificDetails,
            List<String> mustShowElements,
            List<String> mustNotShowElements,
            String realismNotes,
            String compositionHint,
            String cameraView,
            String styleDirection,
            String confidenceLevel,
            List<String> warnings
    ) {
        this(
                paperSceneType, targetUser, userRole, environment, technologyOrProduct,
                technologyFormFactor, mainTask, taskContext, supportAction, expectedOutcome,
                sceneMoment, visibleObjects, visibleInteractions, domainSpecificDetails,
                mustShowElements, mustNotShowElements, realismNotes, compositionHint,
                cameraView, styleDirection, confidenceLevel, warnings, "UNKNOWN"
        );
    }

    @JsonProperty("targetUserOrActor")
    public String targetUserOrActor() {
        return targetUser;
    }

    @JsonProperty("proposedSystemOrMethod")
    public String proposedSystemOrMethod() {
        return technologyOrProduct;
    }

    @JsonProperty("applicationDomain")
    public String applicationDomain() {
        return domainSpecificDetails == null || domainSpecificDetails.isEmpty()
                ? taskContext : domainSpecificDetails.getFirst();
    }

    @JsonProperty("applicationEnvironment")
    public String applicationEnvironment() {
        return environment;
    }

    @JsonProperty("mainTaskOrWorkflow")
    public String mainTaskOrWorkflow() {
        return mainTask;
    }

    @JsonProperty("visibleInterfaceOrDevice")
    public String visibleInterfaceOrDevice() {
        return technologyFormFactor;
    }

    @JsonProperty("visibleInput")
    public String visibleInput() {
        return taskContext;
    }

    @JsonProperty("visibleOutput")
    public String visibleOutput() {
        return supportAction;
    }

    @JsonProperty("visibleInteraction")
    public String visibleInteraction() {
        return visibleInteractions == null || visibleInteractions.isEmpty()
                ? mainTask : visibleInteractions.getFirst();
    }

    @JsonProperty("keyContributionInScene")
    public String keyContributionInScene() {
        return sceneMoment;
    }

    @JsonProperty("mustAvoidElements")
    public List<String> mustAvoidElements() {
        return mustNotShowElements == null ? List.of() : mustNotShowElements;
    }

    @JsonProperty("forbiddenVisualElements")
    public List<String> forbiddenVisualElements() {
        return mustAvoidElements();
    }
}
