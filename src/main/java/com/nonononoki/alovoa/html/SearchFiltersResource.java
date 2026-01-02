package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Page controller for search filters.
 */
@Controller
public class SearchFiltersResource {

    @Autowired
    private AuthService authService;

    @GetMapping("/search-filters")
    public String searchFilters() throws AlovoaException {
        authService.getCurrentUser(true);
        return "search-filters";
    }
}
