package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport.AccountabilityCategory;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport.ReportStatus;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport.ReportVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountabilityReportRepository extends JpaRepository<UserAccountabilityReport, Long> {

    Optional<UserAccountabilityReport> findByUuid(UUID uuid);

    // Find reports about a specific user (subject)
    List<UserAccountabilityReport> findBySubject(User subject);

    // Find published reports about a user
    List<UserAccountabilityReport> findBySubjectAndStatus(User subject, ReportStatus status);

    // Find reports visible to public
    List<UserAccountabilityReport> findBySubjectAndStatusAndVisibility(
        User subject, ReportStatus status, ReportVisibility visibility);

    // Find reports by reporter
    List<UserAccountabilityReport> findByReporter(User reporter);

    // Check if reporter already reported this subject for same category
    Optional<UserAccountabilityReport> findByReporterAndSubjectAndCategory(
        User reporter, User subject, AccountabilityCategory category);

    // Find pending verification reports
    List<UserAccountabilityReport> findByStatus(ReportStatus status);

    Page<UserAccountabilityReport> findByStatus(ReportStatus status, Pageable pageable);

    // Count reports against a user
    long countBySubjectAndStatus(User subject, ReportStatus status);

    // Count reports by category for a user
    long countBySubjectAndStatusAndCategory(User subject, ReportStatus status, AccountabilityCategory category);

    // Find reports with verified evidence
    List<UserAccountabilityReport> findByEvidenceVerifiedTrue();

    // Find reports linked to a conversation
    Optional<UserAccountabilityReport> findByConversationIdAndReporterAndCategory(
        Long conversationId, User reporter, AccountabilityCategory category);

    // Find reports created after a date
    List<UserAccountabilityReport> findByCreatedAtAfter(Date date);

    // Find frequently reported users
    @Query("SELECT r.subject.id, COUNT(r) as reportCount FROM UserAccountabilityReport r " +
           "WHERE r.status = :status GROUP BY r.subject.id HAVING COUNT(r) >= :minCount " +
           "ORDER BY reportCount DESC")
    List<Object[]> findFrequentlyReportedUsers(
        @Param("status") ReportStatus status, @Param("minCount") long minCount);

    // Find reports waiting for subject response
    @Query("SELECT r FROM UserAccountabilityReport r WHERE r.status = 'VERIFIED' " +
           "AND r.subjectResponse IS NULL AND r.createdAt > :since")
    List<UserAccountabilityReport> findPendingSubjectResponse(@Param("since") Date since);

    // Get positive vs negative report counts for user
    @Query("SELECT r.category, COUNT(r) FROM UserAccountabilityReport r " +
           "WHERE r.subject = :subject AND r.status = 'PUBLISHED' GROUP BY r.category")
    List<Object[]> getReportCountsByCategory(@Param("subject") User subject);

    // Find reports by same reporter (for detecting abuse)
    @Query("SELECT COUNT(r) FROM UserAccountabilityReport r WHERE r.reporter = :reporter " +
           "AND r.createdAt > :since")
    long countRecentReportsByReporter(@Param("reporter") User reporter, @Param("since") Date since);

    // Find highly flagged reports (potentially false)
    List<UserAccountabilityReport> findByFlaggedCountGreaterThanOrderByFlaggedCountDesc(int threshold);
}
