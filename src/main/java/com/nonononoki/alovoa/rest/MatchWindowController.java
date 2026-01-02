package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.MatchWindow;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.MatchWindowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for 24-hour match decision windows.
 */
@RestController
@RequestMapping("/api/v1/match-windows")
public class MatchWindowController {

    @Autowired
    private MatchWindowService windowService;

    /**
     * Get all pending decisions for the current user.
     * These are matches waiting for the user to confirm/decline.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<MatchWindow>> getPendingDecisions() throws AlovoaException {
        return ResponseEntity.ok(windowService.getPendingDecisions());
    }

    /**
     * Get matches where user has confirmed but waiting on the other person.
     */
    @GetMapping("/waiting")
    public ResponseEntity<List<MatchWindow>> getWaitingMatches() throws AlovoaException {
        return ResponseEntity.ok(windowService.getWaitingMatches());
    }

    /**
     * Get confirmed matches ready for conversation.
     */
    @GetMapping("/confirmed")
    public ResponseEntity<List<MatchWindow>> getConfirmedMatches() throws AlovoaException {
        return ResponseEntity.ok(windowService.getConfirmedMatches());
    }

    /**
     * Get count of pending decisions (for notification badge).
     */
    @GetMapping("/pending/count")
    public ResponseEntity<Map<String, Integer>> getPendingCount() throws AlovoaException {
        Map<String, Integer> result = new HashMap<>();
        result.put("count", windowService.getPendingCount());
        return ResponseEntity.ok(result);
    }

    /**
     * Get a specific match window.
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<MatchWindow> getWindow(@PathVariable UUID uuid) {
        return windowService.getWindow(uuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Confirm interest in a match.
     */
    @PostMapping("/{uuid}/confirm")
    public ResponseEntity<?> confirmInterest(@PathVariable UUID uuid) {
        try {
            MatchWindow window = windowService.confirmInterest(uuid);
            return ResponseEntity.ok(window);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Decline a match.
     */
    @PostMapping("/{uuid}/decline")
    public ResponseEntity<?> declineMatch(@PathVariable UUID uuid) {
        try {
            MatchWindow window = windowService.declineMatch(uuid);
            return ResponseEntity.ok(window);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Request a 12-hour extension on the decision window.
     */
    @PostMapping("/{uuid}/extend")
    public ResponseEntity<?> requestExtension(@PathVariable UUID uuid) {
        try {
            MatchWindow window = windowService.requestExtension(uuid);
            return ResponseEntity.ok(window);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get dashboard summary for matches page.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() throws AlovoaException {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("pending", windowService.getPendingDecisions());
        dashboard.put("waiting", windowService.getWaitingMatches());
        dashboard.put("confirmed", windowService.getConfirmedMatches());
        dashboard.put("pendingCount", windowService.getPendingCount());
        return ResponseEntity.ok(dashboard);
    }
}
