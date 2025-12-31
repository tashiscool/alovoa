package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.user.ReportEvidence;
import com.nonononoki.alovoa.entity.user.ReportEvidence.EvidenceType;
import com.nonononoki.alovoa.entity.user.ReportEvidence.VerificationMethod;
import com.nonononoki.alovoa.entity.user.UserAccountabilityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, Long> {

    Optional<ReportEvidence> findByUuid(UUID uuid);

    // Find all evidence for a report
    List<ReportEvidence> findByReportOrderByDisplayOrderAsc(UserAccountabilityReport report);

    // Find evidence by type
    List<ReportEvidence> findByReportAndEvidenceType(UserAccountabilityReport report, EvidenceType type);

    // Find unverified evidence
    List<ReportEvidence> findByVerifiedFalse();

    // Find evidence by verification method
    List<ReportEvidence> findByVerificationMethod(VerificationMethod method);

    // Find evidence by image hash (duplicate detection)
    Optional<ReportEvidence> findByImageHash(String imageHash);

    // Find potentially tampered evidence
    List<ReportEvidence> findByAppearsTamperedTrue();

    // Count evidence per report
    long countByReport(UserAccountabilityReport report);

    // Find evidence with matched messages (OCR verified)
    @Query("SELECT e FROM ReportEvidence e WHERE e.matchedMessageIds IS NOT NULL " +
           "AND e.verificationConfidence >= :minConfidence")
    List<ReportEvidence> findVerifiedScreenshots(@Param("minConfidence") Double minConfidence);

    // Delete orphaned evidence (report deleted)
    @Query("DELETE FROM ReportEvidence e WHERE e.report IS NULL")
    void deleteOrphanedEvidence();
}
