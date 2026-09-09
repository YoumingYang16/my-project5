package com.heritage.platform.entity;

import com.heritage.platform.enums.ContributorApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "contributor_applications")
public class ContributorApplication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Column(columnDefinition = "TEXT")
    private String applicationReason;

    @Column(length = 255)
    private String attachmentPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributorApplicationStatus status = ContributorApplicationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(length = 255)
    private String rejectReason;

    protected ContributorApplication() {
    }

    private ContributorApplication(User applicant, String applicationReason, String attachmentPath) {
        this.applicant = applicant;
        this.applicationReason = applicationReason;
        this.attachmentPath = attachmentPath;
        this.status = ContributorApplicationStatus.PENDING;
    }

    public static ContributorApplication create(User applicant, String applicationReason, String attachmentPath) {
        return new ContributorApplication(applicant, applicationReason, attachmentPath);
    }

    public void approve(User reviewer) {
        this.status = ContributorApplicationStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.rejectReason = null;
    }

    public void reject(User reviewer, String reason) {
        this.status = ContributorApplicationStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.rejectReason = reason;
    }

    public Long getId() {
        return id;
    }

    public User getApplicant() {
        return applicant;
    }

    public String getApplicationReason() {
        return applicationReason;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public ContributorApplicationStatus getStatus() {
        return status;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
