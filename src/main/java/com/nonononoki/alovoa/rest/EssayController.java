package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.EssayDto;
import com.nonononoki.alovoa.service.EssayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing user essays (the 10 fixed OKCupid-style profile prompts).
 */
@RestController
@RequestMapping("/api/v1/essay")
public class EssayController {

    @Autowired
    private EssayService essayService;

    /**
     * Get all essays for the current user with templates.
     */
    @GetMapping
    public ResponseEntity<List<EssayDto>> getEssays() throws AlovoaException {
        return ResponseEntity.ok(essayService.getCurrentUserEssays());
    }

    /**
     * Save a single essay.
     */
    @PostMapping("/{promptId}")
    public ResponseEntity<Void> saveEssay(
            @PathVariable Long promptId,
            @RequestBody Map<String, String> body) throws AlovoaException {
        String text = body.get("text");
        essayService.saveEssay(promptId, text);
        return ResponseEntity.ok().build();
    }

    /**
     * Save multiple essays at once.
     * Request body: { "1": "My self summary...", "2": "I work as...", ... }
     */
    @PostMapping("/bulk")
    public ResponseEntity<Void> saveEssays(@RequestBody Map<String, String> essays) throws AlovoaException {
        // Convert string keys to Long
        Map<Long, String> essayMap = essays.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> Long.parseLong(e.getKey()),
                        Map.Entry::getValue
                ));
        essayService.saveEssays(essayMap);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete an essay (clear the answer).
     */
    @DeleteMapping("/{promptId}")
    public ResponseEntity<Void> deleteEssay(@PathVariable Long promptId) throws AlovoaException {
        essayService.saveEssay(promptId, null);
        return ResponseEntity.ok().build();
    }
}
