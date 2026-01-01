package com.nonononoki.alovoa.html;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.ConversationDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ChatResource {

    @Autowired
    private AuthService authService;

    @Autowired
    private ConversationRepository conversationRepo;

    @Autowired
    private UserService userService;

    /**
     * Display the chat page with list of all conversations
     */
    @GetMapping("/chat")
    public String chat(Model model) throws Exception {
        User user = authService.getCurrentUser(true);

        // Get all conversations for the current user
        List<Conversation> conversations = conversationRepo.findByUsers_Id(user.getId());

        // Convert to DTOs and sort by last updated (most recent first)
        List<ConversationDto> conversationDtos = conversations.stream()
            .map(c -> ConversationDto.conversationToDto(c, user, userService))
            .sorted(Comparator.comparing(ConversationDto::getLastUpdated).reversed())
            .collect(Collectors.toList());

        model.addAttribute("user", user);
        model.addAttribute("conversations", conversationDtos);
        model.addAttribute("selectedConversationId", null);

        return "chat";
    }

    /**
     * Display the chat page with a specific conversation open
     */
    @GetMapping("/chat/{conversationId}")
    public String conversation(@PathVariable Long conversationId, Model model) throws Exception {
        User user = authService.getCurrentUser(true);

        // Verify the conversation exists and user has access
        Conversation selectedConversation = conversationRepo.findById(conversationId).orElse(null);

        if (selectedConversation == null) {
            throw new AlovoaException("conversation_not_found");
        }

        if (!selectedConversation.containsUser(user)) {
            throw new AlovoaException("user_not_in_conversation");
        }

        // Get all conversations for the current user
        List<Conversation> conversations = conversationRepo.findByUsers_Id(user.getId());

        // Convert to DTOs and sort by last updated (most recent first)
        List<ConversationDto> conversationDtos = conversations.stream()
            .map(c -> ConversationDto.conversationToDto(c, user, userService))
            .sorted(Comparator.comparing(ConversationDto::getLastUpdated).reversed())
            .collect(Collectors.toList());

        model.addAttribute("user", user);
        model.addAttribute("conversations", conversationDtos);
        model.addAttribute("selectedConversationId", conversationId);

        return "chat";
    }
}
