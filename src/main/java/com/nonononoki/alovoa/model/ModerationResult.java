package com.nonononoki.alovoa.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ModerationResult {
    private boolean allowed;
    private double toxicityScore;
    private List<String> flaggedCategories;
    private String reason;

    public static ModerationResult allowed() {
        return new ModerationResult(true, 0.0, Collections.emptyList(), null);
    }

    public static ModerationResult blocked(double score, List<String> categories, String reason) {
        return new ModerationResult(false, score, categories, reason);
    }
}
