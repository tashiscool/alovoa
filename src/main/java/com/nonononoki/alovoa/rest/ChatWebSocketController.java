package com.nonononoki.alovoa.rest;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.model.MessageDto;
import com.nonononoki.alovoa.model.MessageReactionDto;
import com.nonononoki.alovoa.model.MessageStatusDto;
import com.nonononoki.alovoa.repo.ConversationRepository;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.MessageService;

@Controller
public class ChatWebSocketController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ConversationRepository conversationRepo;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Handles incoming chat messages from WebSocket clients
     *
     * @param conversationId The conversation ID
     * @param messageContent The message content as plain text
     * @param principal The authenticated user principal
     * @throws AlovoaException If validation fails
     * @throws GeneralSecurityException If encryption fails
     * @throws IOException If I/O error occurs
     */
    @MessageMapping("/chat.send/{conversationId}")
    public void sendMessage(@DestinationVariable Long conversationId,
                           @Payload String messageContent,
                           Principal principal)
            throws AlovoaException, GeneralSecurityException, IOException {

        // Save the message using existing service
        messageService.send(conversationId, messageContent);

        // The broadcast is now handled in MessageService
    }

    /**
     * Handles typing indicator events
     *
     * @param conversationId The conversation ID
     * @param principal The authenticated user principal
     * @throws AlovoaException If validation fails
     */
    @MessageMapping("/chat.typing/{conversationId}")
    public void typing(@DestinationVariable Long conversationId, Principal principal)
            throws AlovoaException {

        User currentUser = authService.getCurrentUser(true);
        Conversation conversation = conversationRepo.findById(conversationId).orElse(null);

        if (conversation == null) {
            throw new AlovoaException("conversation_not_found");
        }

        if (!conversation.containsUser(currentUser)) {
            throw new AlovoaException("user_not_in_conversation");
        }

        // Get the partner user
        User partner = conversation.getPartner(currentUser);

        // Send typing indicator to the partner
        messagingTemplate.convertAndSendToUser(
            partner.getUuid().toString(),
            "/queue/typing",
            conversationId
        );
    }

    /**
     * Handles read receipt events when user reads messages
     *
     * @param conversationId The conversation ID
     * @param principal The authenticated user principal
     * @throws AlovoaException If validation fails
     */
    @MessageMapping("/chat.read/{conversationId}")
    public void markAsRead(@DestinationVariable Long conversationId, Principal principal)
            throws AlovoaException {

        User currentUser = authService.getCurrentUser(true);
        messageService.markConversationAsRead(currentUser, conversationId);
    }

    /**
     * Handles delivery receipt events when user opens/connects to a conversation
     *
     * @param conversationId The conversation ID
     * @param principal The authenticated user principal
     * @throws AlovoaException If validation fails
     */
    @MessageMapping("/chat.delivered/{conversationId}")
    public void markAsDelivered(@DestinationVariable Long conversationId, Principal principal)
            throws AlovoaException {

        User currentUser = authService.getCurrentUser(true);
        messageService.markConversationAsDelivered(currentUser, conversationId);
    }

    /**
     * Broadcast a message to a specific user in a conversation
     *
     * @param recipientId The recipient's UUID as string
     * @param messageDto The message DTO to send
     */
    public void broadcastMessage(String recipientId, MessageDto messageDto) {
        messagingTemplate.convertAndSendToUser(
            recipientId,
            "/queue/messages",
            messageDto
        );
    }

    /**
     * Broadcast a read receipt to a specific user
     *
     * @param recipientId The recipient's UUID as string
     * @param statusDto The message status DTO to send
     */
    public void broadcastReadReceipt(String recipientId, MessageStatusDto statusDto) {
        messagingTemplate.convertAndSendToUser(
            recipientId,
            "/queue/receipts",
            statusDto
        );
    }

    /**
     * Handles message reaction events from WebSocket clients
     *
     * @param messageId The message ID to react to
     * @param emoji The emoji reaction as plain text
     * @param principal The authenticated user principal
     * @throws AlovoaException If validation fails
     */
    @MessageMapping("/chat.react/{messageId}")
    public void reactToMessage(@DestinationVariable Long messageId,
                               @Payload String emoji,
                               Principal principal)
            throws AlovoaException {

        User currentUser = authService.getCurrentUser(true);
        messageService.addReaction(currentUser, messageId, emoji);

        // The broadcast is handled in MessageService
    }

    /**
     * Handles message reaction removal events from WebSocket clients
     *
     * @param messageId The message ID to remove reaction from
     * @param principal The authenticated user principal
     * @throws AlovoaException If validation fails
     */
    @MessageMapping("/chat.unreact/{messageId}")
    public void removeReaction(@DestinationVariable Long messageId,
                               Principal principal)
            throws AlovoaException {

        User currentUser = authService.getCurrentUser(true);
        messageService.removeReaction(currentUser, messageId);

        // The broadcast is handled in MessageService
    }

    /**
     * Broadcast a reaction to a specific user
     *
     * @param recipientId The recipient's UUID as string
     * @param reactionDto The reaction DTO to send
     */
    public void broadcastReaction(String recipientId, MessageReactionDto reactionDto) {
        messagingTemplate.convertAndSendToUser(
            recipientId,
            "/queue/reactions",
            reactionDto
        );
    }

    /**
     * Broadcast a reaction removal to a specific user
     *
     * @param recipientId The recipient's UUID as string
     * @param reactionDto The reaction DTO containing message and user info
     */
    public void broadcastReactionRemoval(String recipientId, MessageReactionDto reactionDto) {
        messagingTemplate.convertAndSendToUser(
            recipientId,
            "/queue/reactions/remove",
            reactionDto
        );
    }
}
