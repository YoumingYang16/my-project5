package com.heritage.platform.repository;

import com.heritage.platform.entity.ContributorApplication;
import com.heritage.platform.enums.ContributorApplicationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContributorApplicationRepository extends JpaRepository<ContributorApplication, Long> {

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    List<ContributorApplication> findAllByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    List<ContributorApplication> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    List<ContributorApplication> findAllByStatusOrderByCreatedAtDesc(ContributorApplicationStatus status);

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    List<ContributorApplication> findAllByOrderByApplicantUsernameAsc();

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    List<ContributorApplication> findAllByStatusOrderByApplicantUsernameAsc(ContributorApplicationStatus status);

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    List<ContributorApplication> findAllByApplicantIdAndStatusOrderByCreatedAtDesc(Long applicantId, ContributorApplicationStatus status);

    boolean existsByApplicantIdAndStatus(Long applicantId, ContributorApplicationStatus status);

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    Optional<ContributorApplication> findById(Long id);
}
