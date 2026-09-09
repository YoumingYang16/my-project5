package com.heritage.platform.service.ai;

import com.heritage.platform.common.ForbiddenException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.ai.AiCoverGenerationOptions;
import com.heritage.platform.dto.ai.AiCoverGenerationResult;
import com.heritage.platform.entity.User;
import com.heritage.platform.service.AuthContextService;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiCoverGenerationJobService {

    private static final long JOB_RETENTION_SECONDS = 30 * 60;

    private final PublicationAiCoverService publicationAiCoverService;
    private final AuthContextService authContextService;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public AiCoverGenerationJobService(
            PublicationAiCoverService publicationAiCoverService,
            AuthContextService authContextService
    ) {
        this.publicationAiCoverService = publicationAiCoverService;
        this.authContextService = authContextService;
    }

    public JobStatus start(Long publicationId, AiCoverGenerationOptions options) {
        User user = authContextService.requireActiveUser();
        SecurityContext securityContext = SecurityContextHolder.getContext();
        purgeExpiredJobs();
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, publicationId, user.getId());
        jobs.put(jobId, job);
        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                AiCoverGenerationResult result = publicationAiCoverService.generateForUser(
                        publicationId,
                        options,
                        user
                );
                job.complete(result);
            } catch (RuntimeException ex) {
                job.fail(ex.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return job.status();
    }

    public JobStatus status(String jobId) {
        User user = authContextService.requireActiveUser();
        purgeExpiredJobs();
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("The image generation task could not be found or has expired.");
        }
        if (!job.ownerId.equals(user.getId())) {
            throw new ForbiddenException("You cannot view this image generation task.");
        }
        return job.status();
    }

    private void purgeExpiredJobs() {
        Instant cutoff = Instant.now().minusSeconds(JOB_RETENTION_SECONDS);
        jobs.values().removeIf(job -> job.finishedAt != null && job.finishedAt.isBefore(cutoff));
    }

    public record JobStatus(
            String jobId,
            Long publicationId,
            String status,
            String message,
            AiCoverGenerationResult result
    ) {
    }

    private static final class Job {
        private final String jobId;
        private final Long publicationId;
        private final Long ownerId;
        private volatile String status = "RUNNING";
        private volatile String message = "Image generation is running.";
        private volatile AiCoverGenerationResult result;
        private volatile Instant finishedAt;

        private Job(String jobId, Long publicationId, Long ownerId) {
            this.jobId = jobId;
            this.publicationId = publicationId;
            this.ownerId = ownerId;
        }

        private void complete(AiCoverGenerationResult value) {
            result = value;
            status = "COMPLETED";
            message = value == null ? "Image generation completed." : value.message();
            finishedAt = Instant.now();
        }

        private void fail(String failure) {
            status = "FAILED";
            message = failure == null || failure.isBlank() ? "Image generation failed." : failure;
            finishedAt = Instant.now();
        }

        private JobStatus status() {
            return new JobStatus(jobId, publicationId, status, message, result);
        }
    }
}
