package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.MentalHealthResource;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.AccountPause;
import com.nonononoki.alovoa.entity.user.InterventionDelivery;
import com.nonononoki.alovoa.repo.AccountPauseRepository;
import com.nonononoki.alovoa.repo.InterventionDeliveryRepository;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.InterventionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for intervention system.
 * Handles notification delivery, resource tracking, and account pause management.
 */
@RestController
@RequestMapping("/api/intervention")
public class InterventionController {

    @Autowired
    private InterventionService interventionService;

    @Autowired
    private InterventionDeliveryRepository interventionRepo;

    @Autowired
    private AccountPauseRepository pauseRepo;

    @Autowired
    private AuthService authService;

    /**
     * Get current account pause status if any
     */
    @GetMapping("/pause/status")
    public ResponseEntity<Map<String, Object>> getPauseStatus() throws Exception {
        User user = authService.getCurrentUser();
        Map<String, Object> response = new HashMap<>();

        Optional<AccountPause> pauseOpt = pauseRepo.findByUserAndResumedAtIsNull(user);
        if (pauseOpt.isPresent()) {
            AccountPause pause = pauseOpt.get();
            response.put("paused", true);
            response.put("pauseUuid", pause.getUuid().toString());
            response.put("reason", pause.getPauseReason());
            response.put("pauseType", pause.getPauseType().name());
            response.put("pausedAt", pause.getPausedAt());
            response.put("pauseUntil", pause.getPauseUntil());
            response.put("canAppeal", pause.getCanAppeal());
            response.put("appealSubmitted", pause.getAppealSubmitted());
            response.put("resourcesProvided", pause.getResourcesProvided());
        } else {
            response.put("paused", false);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get mental health resources for the current user
     */
    @GetMapping("/resources")
    public ResponseEntity<List<Map<String, Object>>> getResources() throws Exception {
        User user = authService.getCurrentUser();
        List<MentalHealthResource> resources = interventionService.getResourcesForUser(user.getCountry());

        List<Map<String, Object>> response = new ArrayList<>();
        for (MentalHealthResource resource : resources) {
            Map<String, Object> r = new HashMap<>();
            r.put("name", resource.getName());
            r.put("type", resource.getResourceType().name());
            r.put("description", resource.getDescription());
            r.put("contactInfo", resource.getContactInfo());
            r.put("url", resource.getUrl());
            r.put("available247", resource.getAvailable247());
            response.add(r);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get unread intervention messages
     */
    @GetMapping("/unread")
    public ResponseEntity<List<Map<String, Object>>> getUnreadInterventions() throws Exception {
        User user = authService.getCurrentUser();
        List<InterventionDelivery> unread = interventionRepo.findByUserAndReadAtIsNullOrderByDeliveredAtDesc(user);

        List<Map<String, Object>> response = new ArrayList<>();
        for (InterventionDelivery delivery : unread) {
            Map<String, Object> d = new HashMap<>();
            d.put("uuid", delivery.getUuid().toString());
            d.put("tier", delivery.getInterventionTier());
            d.put("messageType", delivery.getMessageType().name());
            d.put("message", delivery.getMessageContent());
            d.put("channel", delivery.getDeliveryChannel().name());
            d.put("deliveredAt", delivery.getDeliveredAt());
            response.add(d);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Mark intervention as read
     */
    @PostMapping("/{uuid}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID uuid) throws Exception {
        User user = authService.getCurrentUser();
        Optional<InterventionDelivery> deliveryOpt = interventionRepo.findByUuid(uuid);

        if (deliveryOpt.isPresent()) {
            InterventionDelivery delivery = deliveryOpt.get();
            if (delivery.getUser().getId().equals(user.getId())) {
                delivery.setReadAt(new Date());
                interventionRepo.save(delivery);
            }
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Mark that resources were clicked
     */
    @PostMapping("/{uuid}/resources-clicked")
    public ResponseEntity<Void> markResourcesClicked(@PathVariable UUID uuid) throws Exception {
        User user = authService.getCurrentUser();
        interventionService.markResourcesClicked(user, uuid);
        return ResponseEntity.ok().build();
    }

    /**
     * Submit appeal for account pause
     */
    @PostMapping("/pause/appeal")
    public ResponseEntity<Map<String, Object>> submitAppeal(@RequestBody Map<String, String> request) throws Exception {
        User user = authService.getCurrentUser();
        Map<String, Object> response = new HashMap<>();

        Optional<AccountPause> pauseOpt = pauseRepo.findByUserAndResumedAtIsNull(user);
        if (pauseOpt.isEmpty()) {
            response.put("success", false);
            response.put("error", "No active pause found");
            return ResponseEntity.badRequest().body(response);
        }

        AccountPause pause = pauseOpt.get();
        if (!pause.getCanAppeal()) {
            response.put("success", false);
            response.put("error", "Appeals are not available for this pause");
            return ResponseEntity.badRequest().body(response);
        }

        if (pause.getAppealSubmitted()) {
            response.put("success", false);
            response.put("error", "Appeal already submitted");
            return ResponseEntity.badRequest().body(response);
        }

        pause.setAppealSubmitted(true);
        pauseRepo.save(pause);

        response.put("success", true);
        response.put("message", "Your appeal has been submitted. We'll review it and get back to you.");

        return ResponseEntity.ok(response);
    }

    /**
     * Record user response to intervention
     */
    @PostMapping("/{uuid}/respond")
    public ResponseEntity<Void> respondToIntervention(
            @PathVariable UUID uuid,
            @RequestBody Map<String, String> request) throws Exception {
        User user = authService.getCurrentUser();
        Optional<InterventionDelivery> deliveryOpt = interventionRepo.findByUuid(uuid);

        if (deliveryOpt.isPresent()) {
            InterventionDelivery delivery = deliveryOpt.get();
            if (delivery.getUser().getId().equals(user.getId())) {
                String response = request.get("response");
                if (response != null && response.length() <= 500) {
                    delivery.setUserResponse(response);
                    delivery.setReadAt(new Date());
                    interventionRepo.save(delivery);
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Get intervention history for current user
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory() throws Exception {
        User user = authService.getCurrentUser();
        List<InterventionDelivery> history = interventionRepo.findByUserOrderByDeliveredAtDesc(user);

        List<Map<String, Object>> response = new ArrayList<>();
        for (InterventionDelivery delivery : history) {
            Map<String, Object> d = new HashMap<>();
            d.put("uuid", delivery.getUuid().toString());
            d.put("tier", delivery.getInterventionTier());
            d.put("messageType", delivery.getMessageType().name());
            d.put("deliveredAt", delivery.getDeliveredAt());
            d.put("readAt", delivery.getReadAt());
            d.put("resourcesClicked", delivery.getResourcesClicked());
            response.add(d);
        }

        return ResponseEntity.ok(response);
    }
}
