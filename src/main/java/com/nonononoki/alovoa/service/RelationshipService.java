package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserRelationship;
import com.nonononoki.alovoa.entity.UserRelationship.RelationshipStatus;
import com.nonononoki.alovoa.entity.UserRelationship.RelationshipType;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.RelationshipDto;
import com.nonononoki.alovoa.repo.UserRelationshipRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing linked relationships between users.
 */
@Service
public class RelationshipService {

    @Autowired
    private UserRelationshipRepository relationshipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private DonationService donationService;

    /**
     * Get the current user's active relationship.
     */
    public Optional<RelationshipDto> getMyRelationship() {
        User user = authService.getCurrentUser(true);
        return relationshipRepository.findActiveRelationshipByUser(user)
                .map(r -> toDto(r, user));
    }

    /**
     * Get a user's public relationship (for profile display).
     */
    public Optional<RelationshipDto> getPublicRelationship(User user) {
        return relationshipRepository.findActiveRelationshipByUser(user)
                .filter(r -> r.getIsPublic())
                .map(r -> toDto(r, user));
    }

    /**
     * Get pending relationship requests for the current user.
     */
    public List<RelationshipDto> getPendingRequests() {
        User user = authService.getCurrentUser(true);
        return relationshipRepository.findPendingRequestsForUser(user)
                .stream()
                .map(r -> toDto(r, user))
                .collect(Collectors.toList());
    }

    /**
     * Get relationship requests sent by the current user.
     */
    public List<RelationshipDto> getSentRequests() {
        User user = authService.getCurrentUser(true);
        return relationshipRepository.findPendingRequestsByUser(user)
                .stream()
                .map(r -> toDto(r, user))
                .collect(Collectors.toList());
    }

    /**
     * Send a relationship request to another user.
     */
    @Transactional
    public RelationshipDto sendRequest(UUID partnerUuid, RelationshipType type, Date anniversaryDate)
            throws AlovoaException {

        User currentUser = authService.getCurrentUser(true);
        User partner = userRepository.findByUuid(partnerUuid)
                .orElseThrow(() -> new AlovoaException("user_not_found"));

        // Can't link to yourself
        if (currentUser.getId().equals(partner.getId())) {
            throw new AlovoaException("cannot_link_to_self");
        }

        // Check if there's already an existing relationship
        Optional<UserRelationship> existing = relationshipRepository
                .findExistingRelationship(currentUser, partner);
        if (existing.isPresent()) {
            UserRelationship rel = existing.get();
            if (rel.getStatus() == RelationshipStatus.PENDING) {
                throw new AlovoaException("request_already_pending");
            } else if (rel.getStatus() == RelationshipStatus.CONFIRMED) {
                throw new AlovoaException("already_in_relationship");
            }
        }

        // Check if current user already has an active relationship
        Optional<UserRelationship> activeRel = relationshipRepository.findActiveRelationshipByUser(currentUser);
        if (activeRel.isPresent()) {
            throw new AlovoaException("already_in_relationship");
        }

        // Create the relationship request
        UserRelationship relationship = new UserRelationship();
        relationship.setUser1(currentUser);
        relationship.setUser2(partner);
        relationship.setType(type);
        relationship.setStatus(RelationshipStatus.PENDING);
        relationship.setAnniversaryDate(anniversaryDate);
        relationship.setIsPublic(true);

        relationship = relationshipRepository.save(relationship);

        return toDto(relationship, currentUser);
    }

    /**
     * Accept a relationship request.
     */
    @Transactional
    public RelationshipDto acceptRequest(UUID relationshipUuid) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        UserRelationship relationship = relationshipRepository.findByUuid(relationshipUuid)
                .orElseThrow(() -> new AlovoaException("relationship_not_found"));

        // Must be the recipient (user2) to accept
        if (!relationship.getUser2().getId().equals(currentUser.getId())) {
            throw new AlovoaException("not_authorized");
        }

        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new AlovoaException("request_not_pending");
        }

        // Check if accepting user already has an active relationship
        Optional<UserRelationship> activeRel = relationshipRepository.findActiveRelationshipByUser(currentUser);
        if (activeRel.isPresent()) {
            throw new AlovoaException("already_in_relationship");
        }

        relationship.setStatus(RelationshipStatus.CONFIRMED);
        relationship.setConfirmedAt(new Date());
        relationship = relationshipRepository.save(relationship);

        // Trigger donation prompt for relationship milestone
        try {
            donationService.triggerRelationshipMilestone(currentUser);
            donationService.triggerRelationshipMilestone(relationship.getUser1());
        } catch (Exception ignored) {}

        return toDto(relationship, currentUser);
    }

    /**
     * Decline a relationship request.
     */
    @Transactional
    public void declineRequest(UUID relationshipUuid) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        UserRelationship relationship = relationshipRepository.findByUuid(relationshipUuid)
                .orElseThrow(() -> new AlovoaException("relationship_not_found"));

        // Must be the recipient (user2) to decline
        if (!relationship.getUser2().getId().equals(currentUser.getId())) {
            throw new AlovoaException("not_authorized");
        }

        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new AlovoaException("request_not_pending");
        }

        relationship.setStatus(RelationshipStatus.DECLINED);
        relationshipRepository.save(relationship);
    }

    /**
     * Cancel a sent relationship request.
     */
    @Transactional
    public void cancelRequest(UUID relationshipUuid) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        UserRelationship relationship = relationshipRepository.findByUuid(relationshipUuid)
                .orElseThrow(() -> new AlovoaException("relationship_not_found"));

        // Must be the sender (user1) to cancel
        if (!relationship.getUser1().getId().equals(currentUser.getId())) {
            throw new AlovoaException("not_authorized");
        }

        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new AlovoaException("request_not_pending");
        }

        relationshipRepository.delete(relationship);
    }

    /**
     * End an active relationship.
     */
    @Transactional
    public void endRelationship(UUID relationshipUuid) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        UserRelationship relationship = relationshipRepository.findByUuid(relationshipUuid)
                .orElseThrow(() -> new AlovoaException("relationship_not_found"));

        // Must be part of the relationship to end it
        if (!relationship.involvesUser(currentUser)) {
            throw new AlovoaException("not_authorized");
        }

        if (relationship.getStatus() != RelationshipStatus.CONFIRMED) {
            throw new AlovoaException("relationship_not_active");
        }

        relationship.setStatus(RelationshipStatus.ENDED);
        relationshipRepository.save(relationship);

        // Trigger donation prompt for relationship exit (AURA helped them find someone!)
        try {
            donationService.triggerRelationshipExit(currentUser);
        } catch (Exception ignored) {}
    }

    /**
     * Update relationship type (e.g., dating -> engaged).
     */
    @Transactional
    public RelationshipDto updateRelationshipType(UUID relationshipUuid, RelationshipType newType)
            throws AlovoaException {

        User currentUser = authService.getCurrentUser(true);
        UserRelationship relationship = relationshipRepository.findByUuid(relationshipUuid)
                .orElseThrow(() -> new AlovoaException("relationship_not_found"));

        if (!relationship.involvesUser(currentUser)) {
            throw new AlovoaException("not_authorized");
        }

        if (relationship.getStatus() != RelationshipStatus.CONFIRMED) {
            throw new AlovoaException("relationship_not_active");
        }

        relationship.setType(newType);
        relationship = relationshipRepository.save(relationship);

        return toDto(relationship, currentUser);
    }

    /**
     * Toggle relationship visibility.
     */
    @Transactional
    public RelationshipDto toggleVisibility(UUID relationshipUuid) throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        UserRelationship relationship = relationshipRepository.findByUuid(relationshipUuid)
                .orElseThrow(() -> new AlovoaException("relationship_not_found"));

        if (!relationship.involvesUser(currentUser)) {
            throw new AlovoaException("not_authorized");
        }

        relationship.setIsPublic(!relationship.getIsPublic());
        relationship = relationshipRepository.save(relationship);

        return toDto(relationship, currentUser);
    }

    /**
     * Convert entity to DTO.
     */
    private RelationshipDto toDto(UserRelationship relationship, User currentUser) {
        User partner = relationship.getPartner(currentUser);
        boolean isInitiator = relationship.isInitiator(currentUser);

        return RelationshipDto.builder()
                .uuid(relationship.getUuid())
                .type(relationship.getType())
                .typeDisplayName(relationship.getType().getDisplayName())
                .status(relationship.getStatus())
                .partnerUuid(partner.getUuid())
                .partnerName(partner.getFirstName())
                .partnerHasProfilePicture(partner.getProfilePicture() != null)
                .isInitiator(isInitiator)
                .isPublic(relationship.getIsPublic())
                .createdAt(relationship.getCreatedAt())
                .confirmedAt(relationship.getConfirmedAt())
                .anniversaryDate(relationship.getAnniversaryDate())
                .build();
    }
}
