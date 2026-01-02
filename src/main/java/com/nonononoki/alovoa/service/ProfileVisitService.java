package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserProfileVisit;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.repo.UserProfileVisitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Service for tracking profile visits (OKCupid "Visitors" feature).
 */
@Service
public class ProfileVisitService {

    @Autowired
    private UserProfileVisitRepository visitRepository;

    @Autowired
    private AuthService authService;

    /**
     * Record a profile visit. Creates new record or updates existing one.
     */
    @Transactional
    public void recordVisit(User visitor, User visitedUser) {
        // Don't record self-visits
        if (visitor.getId().equals(visitedUser.getId())) {
            return;
        }

        Optional<UserProfileVisit> existingVisit = visitRepository
                .findByVisitorAndVisitedUser(visitor, visitedUser);

        if (existingVisit.isPresent()) {
            existingVisit.get().recordVisit();
            visitRepository.save(existingVisit.get());
        } else {
            UserProfileVisit newVisit = new UserProfileVisit(visitor, visitedUser);
            visitRepository.save(newVisit);
        }
    }

    /**
     * Get visitors to the current user's profile (paginated).
     */
    public Page<UserProfileVisit> getMyVisitors(int page, int size) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        return visitRepository.findByVisitedUserOrderByLastVisitAtDesc(
                currentUser, PageRequest.of(page, size));
    }

    /**
     * Get profiles the current user has visited (paginated).
     */
    public Page<UserProfileVisit> getMyVisitedProfiles(int page, int size) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        return visitRepository.findByVisitorOrderByLastVisitAtDesc(
                currentUser, PageRequest.of(page, size));
    }

    /**
     * Get visitors from the last N days.
     */
    public List<UserProfileVisit> getRecentVisitors(int days) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return visitRepository.findRecentVisitors(currentUser, cal.getTime());
    }

    /**
     * Get profiles the current user has visited in the last N days.
     */
    public List<UserProfileVisit> getMyVisits(int days) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return visitRepository.findRecentVisitsByVisitor(currentUser, cal.getTime());
    }

    /**
     * Count total unique visitors to user's profile.
     */
    public long getTotalVisitorCount() throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        return visitRepository.countByVisitedUser(currentUser);
    }

    /**
     * Count visitors in the last N days.
     */
    public long getRecentVisitorCount(int days) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return visitRepository.countRecentVisitors(currentUser, cal.getTime());
    }

    /**
     * Check if a user has visited another user's profile.
     */
    public boolean hasVisited(User visitor, User visitedUser) {
        return visitRepository.existsByVisitorAndVisitedUser(visitor, visitedUser);
    }

    /**
     * Clean up old visit records (for data retention).
     */
    @Transactional
    public void cleanupOldVisits(int daysToKeep) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -daysToKeep);
        visitRepository.deleteByLastVisitAtBefore(cal.getTime());
    }
}
