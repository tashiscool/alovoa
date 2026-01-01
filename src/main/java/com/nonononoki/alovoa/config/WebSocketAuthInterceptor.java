package com.nonononoki.alovoa.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;

import java.security.Principal;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private AuthService authService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Get the authentication from the HTTP session
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                try {
                    // Verify the user exists and is valid
                    User user = authService.getCurrentUser(true);
                    if (user != null) {
                        // Set the user as the principal for WebSocket session
                        accessor.setUser(new Principal() {
                            @Override
                            public String getName() {
                                return user.getUuid().toString();
                            }
                        });
                    }
                } catch (AlovoaException e) {
                    // Authentication failed - reject connection
                    return null;
                }
            } else {
                // No authentication - reject connection
                return null;
            }
        }

        return message;
    }
}
