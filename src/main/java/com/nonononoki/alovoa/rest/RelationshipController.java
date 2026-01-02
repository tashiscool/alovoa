package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.UserRelationship.RelationshipType;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.RelationshipDto;
import com.nonononoki.alovoa.service.RelationshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing linked relationships between users.
 */
@RestController
@RequestMapping("/api/v1/relationship")
public class RelationshipController {

    @Autowired
    private RelationshipService relationshipService;

    /**
     * Get current user's active relationship.
     */
    @GetMapping
    public ResponseEntity<RelationshipDto> getMyRelationship() throws AlovoaException {
        return relationshipService.getMyRelationship()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Get pending relationship requests for the current user.
     */
    @GetMapping("/requests/pending")
    public ResponseEntity<List<RelationshipDto>> getPendingRequests() throws AlovoaException {
        return ResponseEntity.ok(relationshipService.getPendingRequests());
    }

    /**
     * Get relationship requests sent by the current user.
     */
    @GetMapping("/requests/sent")
    public ResponseEntity<List<RelationshipDto>> getSentRequests() throws AlovoaException {
        return ResponseEntity.ok(relationshipService.getSentRequests());
    }

    /**
     * Get available relationship types.
     */
    @GetMapping("/types")
    public ResponseEntity<RelationshipType[]> getRelationshipTypes() {
        return ResponseEntity.ok(RelationshipType.values());
    }

    /**
     * Send a relationship request to another user.
     */
    @PostMapping("/request")
    public ResponseEntity<RelationshipDto> sendRequest(@RequestBody Map<String, Object> request)
            throws AlovoaException {

        UUID partnerUuid = UUID.fromString((String) request.get("partnerUuid"));
        RelationshipType type = RelationshipType.valueOf((String) request.get("type"));

        Date anniversaryDate = null;
        if (request.containsKey("anniversaryDate") && request.get("anniversaryDate") != null) {
            // Parse ISO date string
            try {
                String dateStr = (String) request.get("anniversaryDate");
                anniversaryDate = java.sql.Date.valueOf(dateStr);
            } catch (Exception ignored) {}
        }

        RelationshipDto dto = relationshipService.sendRequest(partnerUuid, type, anniversaryDate);
        return ResponseEntity.ok(dto);
    }

    /**
     * Accept a relationship request.
     */
    @PostMapping("/{uuid}/accept")
    public ResponseEntity<RelationshipDto> acceptRequest(@PathVariable UUID uuid) throws AlovoaException {
        return ResponseEntity.ok(relationshipService.acceptRequest(uuid));
    }

    /**
     * Decline a relationship request.
     */
    @PostMapping("/{uuid}/decline")
    public ResponseEntity<Void> declineRequest(@PathVariable UUID uuid) throws AlovoaException {
        relationshipService.declineRequest(uuid);
        return ResponseEntity.ok().build();
    }

    /**
     * Cancel a sent relationship request.
     */
    @DeleteMapping("/{uuid}/cancel")
    public ResponseEntity<Void> cancelRequest(@PathVariable UUID uuid) throws AlovoaException {
        relationshipService.cancelRequest(uuid);
        return ResponseEntity.ok().build();
    }

    /**
     * End an active relationship.
     */
    @PostMapping("/{uuid}/end")
    public ResponseEntity<Void> endRelationship(@PathVariable UUID uuid) throws AlovoaException {
        relationshipService.endRelationship(uuid);
        return ResponseEntity.ok().build();
    }

    /**
     * Update relationship type.
     */
    @PutMapping("/{uuid}/type")
    public ResponseEntity<RelationshipDto> updateType(
            @PathVariable UUID uuid,
            @RequestBody Map<String, String> request) throws AlovoaException {

        RelationshipType newType = RelationshipType.valueOf(request.get("type"));
        return ResponseEntity.ok(relationshipService.updateRelationshipType(uuid, newType));
    }

    /**
     * Toggle relationship visibility.
     */
    @PostMapping("/{uuid}/toggle-visibility")
    public ResponseEntity<RelationshipDto> toggleVisibility(@PathVariable UUID uuid) throws AlovoaException {
        return ResponseEntity.ok(relationshipService.toggleVisibility(uuid));
    }
}
