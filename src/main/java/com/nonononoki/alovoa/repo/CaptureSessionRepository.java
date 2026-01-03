package com.nonononoki.alovoa.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nonononoki.alovoa.entity.CaptureSession;
import com.nonononoki.alovoa.entity.CaptureSession.CaptureStatus;
import com.nonononoki.alovoa.entity.User;

public interface CaptureSessionRepository extends JpaRepository<CaptureSession, Long> {

    Optional<CaptureSession> findByCaptureId(UUID captureId);

    Optional<CaptureSession> findByCaptureIdAndUser(UUID captureId, User user);

    List<CaptureSession> findByUserOrderByCreatedAtDesc(User user);

    List<CaptureSession> findByUserAndStatusOrderByCreatedAtDesc(User user, CaptureStatus status);

    @Query("SELECT c FROM CaptureSession c WHERE c.user = :user AND c.status = 'READY' ORDER BY c.createdAt DESC")
    List<CaptureSession> findReadyCapturesByUser(@Param("user") User user);

    @Query("SELECT c FROM CaptureSession c WHERE c.status = 'UPLOADED' ORDER BY c.uploadedAt ASC")
    List<CaptureSession> findPendingProcessing();

    @Query("SELECT COUNT(c) FROM CaptureSession c WHERE c.user = :user AND c.status = 'PENDING'")
    long countPendingByUser(@Param("user") User user);

    void deleteByUserAndCaptureId(User user, UUID captureId);
}
