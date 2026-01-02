package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.UserRelationship.RelationshipType;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.RelationshipDto;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.RelationshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Optional;

/**
 * Page controller for relationship management.
 */
@Controller
public class RelationshipResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private RelationshipService relationshipService;

    @GetMapping("/relationship")
    public String relationshipPage(Model model) throws AlovoaException {
        authService.getCurrentUser(true);

        Optional<RelationshipDto> activeRelationship = relationshipService.getMyRelationship();
        List<RelationshipDto> pendingRequests = relationshipService.getPendingRequests();
        List<RelationshipDto> sentRequests = relationshipService.getSentRequests();

        model.addAttribute("activeRelationship", activeRelationship.orElse(null));
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("sentRequests", sentRequests);
        model.addAttribute("relationshipTypes", RelationshipType.values());
        model.addAttribute("pendingCount", pendingRequests.size());

        return "relationship";
    }
}
