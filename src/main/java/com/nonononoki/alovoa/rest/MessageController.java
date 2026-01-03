package com.nonononoki.alovoa.rest;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.MessageDto;
import com.nonononoki.alovoa.model.MessageReactionDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.MessageService;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/message")
public class MessageController {

	@Autowired
	private ConversationRepository conversationRepo;

	@Autowired
	private AuthService authService;

	@Autowired
	private MessageService messageService;
	
	private final int MAX_MESSAGES = 50;

	@ResponseBody
	@PostMapping(value = "/send/{convoId}", consumes = "text/plain")
	public void send(@RequestBody String msg, @PathVariable long convoId)
			throws AlovoaException, GeneralSecurityException, IOException {
		messageService.send(convoId, msg);
	}

	@ResponseBody
	@PostMapping(value = "/read/{conversationId}")
	public void markAsRead(@PathVariable Long conversationId) throws AlovoaException {
		User user = authService.getCurrentUser(true);
		messageService.markConversationAsRead(user, conversationId);
	}

	@ResponseBody
	@PostMapping(value = "/delivered/{conversationId}")
	public void markAsDelivered(@PathVariable Long conversationId) throws AlovoaException {
		User user = authService.getCurrentUser(true);
		messageService.markConversationAsDelivered(user, conversationId);
	}

	@ResponseBody
	@PostMapping(value = "/{messageId}/react", consumes = "text/plain")
	public ResponseEntity<MessageReactionDto> addReaction(@PathVariable Long messageId,
	                                                      @RequestBody String emoji) throws AlovoaException {
		User user = authService.getCurrentUser(true);
		MessageReactionDto reaction = messageService.addReaction(user, messageId, emoji);
		return ResponseEntity.ok(reaction);
	}

	@ResponseBody
	@DeleteMapping(value = "/{messageId}/react")
	public ResponseEntity<Void> removeReaction(@PathVariable Long messageId) throws AlovoaException {
		User user = authService.getCurrentUser(true);
		messageService.removeReaction(user, messageId);
		return ResponseEntity.ok().build();
	}

	@GetMapping(value = "/get-messages/{convoId}/{first}")
	public String getMessages(Model model, @PathVariable long convoId, @PathVariable int first) throws AlovoaException {

		model = getMessagesModel(model, convoId, first);
		boolean show = (boolean) model.getAttribute("show");
		if (show) {
			return "fragments :: message-detail";
		} else {
			return "fragments :: empty";
		}
	}

	/**
	 * JSON endpoint for messages - use this instead of the HTML fragment endpoint.
	 * Returns paginated messages with full metadata for client-side rendering.
	 *
	 * @param conversationId The conversation ID
	 * @param page Page number (1-indexed)
	 * @return JSON with messages array and pagination info
	 */
	@ResponseBody
	@GetMapping(value = "/api/v1/messages/{conversationId}")
	public ResponseEntity<Map<String, Object>> getMessagesJson(
			@PathVariable Long conversationId,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page) throws AlovoaException {

		User user = authService.getCurrentUser(true);
		Conversation c = conversationRepo.findById(conversationId).orElse(null);

		if (c == null) {
			throw new AlovoaException("conversation_not_found");
		}

		if (!c.containsUser(user)) {
			throw new AlovoaException("user_not_in_conversation");
		}

		User partner = c.getPartner(user);

		// Check blocks
		if (user.getBlockedUsers().stream().filter(o -> o.getUserTo().getId() != null)
				.anyMatch(o -> o.getUserTo().getId().equals(partner.getId()))) {
			throw new AlovoaException("user_blocked");
		}

		if (partner.getBlockedUsers().stream().filter(o -> o.getUserTo().getId() != null)
				.anyMatch(o -> o.getUserTo().getId().equals(user.getId()))) {
			throw new AlovoaException("user_blocked");
		}

		// Get messages
		List<MessageDto> allMessages = MessageDto.messagesToDtos(c.getMessages(), user);
		int totalMessages = allMessages.size();
		int pageSize = MAX_MESSAGES;
		int start = Math.max(totalMessages - (page * pageSize), 0);
		int end = Math.max(totalMessages - ((page - 1) * pageSize), 0);

		List<MessageDto> pageMessages = allMessages.subList(start, end);
		boolean hasMore = start > 0;

		Map<String, Object> response = new HashMap<>();
		response.put("conversationId", conversationId);
		response.put("page", page);
		response.put("hasMore", hasMore);
		response.put("totalMessages", totalMessages);
		response.put("messages", pageMessages);
		response.put("currentUserId", user.getId());

		return ResponseEntity.ok(response);
	}

	/**
	 * Get reactions for a specific message (for updating reactions after WebSocket events)
	 */
	@ResponseBody
	@GetMapping(value = "/api/v1/messages/{messageId}/reactions")
	public ResponseEntity<List<MessageReactionDto>> getMessageReactions(@PathVariable Long messageId) throws AlovoaException {
		List<MessageReactionDto> reactions = messageService.getMessageReactions(messageId);
		return ResponseEntity.ok(reactions);
	}

	public Model getMessagesModel(Model model, long convoId, int first) throws AlovoaException {
		User user = authService.getCurrentUser(true);
		Conversation c = conversationRepo.findById(convoId).orElse(null);

		if (c == null) {
			throw new AlovoaException("conversation_not_found");
		}

		if (!c.containsUser(user)) {
			throw new AlovoaException("user_not_in_conversation");
		}

		User partner = c.getPartner(user);

		if (user.getBlockedUsers().stream().filter(o -> o.getUserTo().getId() != null)
                .anyMatch(o -> o.getUserTo().getId().equals(partner.getId()))) {
			throw new AlovoaException("user_blocked");
		}

		if (partner.getBlockedUsers().stream().filter(o -> o.getUserTo().getId() != null)
                .anyMatch(o -> o.getUserTo().getId().equals(user.getId()))) {
			throw new AlovoaException("user_blocked");
		}

		Date lastCheckedDate = messageService.updateCheckedDate(c);

		if(model == null) {
			model = new ConcurrentModel();
		}
		
		boolean show = first == 1 || lastCheckedDate == null || !lastCheckedDate.after(c.getLastUpdated());
		model.addAttribute("show", show);
		if(show) {
			List<MessageDto> messages = MessageDto.messagesToDtos(c.getMessages(), user);
			messages = messages.subList(Math.max(messages.size() - MAX_MESSAGES, 0), messages.size());
			model.addAttribute("messages", messages);
		}
		return model;
	}
}
