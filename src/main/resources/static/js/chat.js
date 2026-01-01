/**
 * AURA Chat - WebSocket Client
 * Real-time messaging with SockJS and STOMP
 */

// Global state
let stompClient = null;
let currentConversationId = selectedConversationId;
let currentMessages = [];
let typingTimeout = null;
let reactionMessageId = null;

// Initialize chat on page load
document.addEventListener('DOMContentLoaded', function () {
    initializeWebSocket();

    // Load initial conversation if one is selected
    if (currentConversationId) {
        loadConversation(currentConversationId);
    }
});

/**
 * Initialize WebSocket connection
 */
function initializeWebSocket() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // Configure STOMP client
    stompClient.debug = function (str) {
        // Disable STOMP debug logging in production
        // console.log(str);
    };

    stompClient.connect({}, function (frame) {
        console.log('WebSocket connected: ' + frame);

        // Subscribe to incoming messages
        stompClient.subscribe('/user/queue/messages', function (message) {
            const messageDto = JSON.parse(message.body);
            handleIncomingMessage(messageDto);
        });

        // Subscribe to typing indicators
        stompClient.subscribe('/user/queue/typing', function (data) {
            const conversationId = JSON.parse(data.body);
            handleTypingIndicator(conversationId);
        });

        // Subscribe to read receipts
        stompClient.subscribe('/user/queue/receipts', function (data) {
            const statusDto = JSON.parse(data.body);
            handleReadReceipt(statusDto);
        });

        // Subscribe to message reactions
        stompClient.subscribe('/user/queue/reactions', function (data) {
            const reactionDto = JSON.parse(data.body);
            handleReactionUpdate(reactionDto);
        });

        // Subscribe to reaction removals
        stompClient.subscribe('/user/queue/reactions/remove', function (data) {
            const reactionDto = JSON.parse(data.body);
            handleReactionRemoval(reactionDto);
        });

        // Mark current conversation as delivered if connected
        if (currentConversationId) {
            markAsDelivered(currentConversationId);
        }
    }, function (error) {
        console.error('WebSocket connection error:', error);
        // Attempt reconnect after 5 seconds
        setTimeout(initializeWebSocket, 5000);
    });
}

/**
 * Load conversation messages
 */
function loadConversation(conversationId) {
    currentConversationId = conversationId;

    // Update active conversation in sidebar
    document.querySelectorAll('.chat-conversation-item').forEach(item => {
        item.classList.remove('active');
    });
    const conversationItem = document.getElementById('conversation-' + conversationId);
    if (conversationItem) {
        conversationItem.classList.add('active');
    }

    // Load messages via REST API
    fetch('/message/get-messages/' + conversationId + '/1')
        .then(response => response.text())
        .then(html => {
            const messagesContainer = document.getElementById('chat-messages');
            if (messagesContainer) {
                messagesContainer.innerHTML = '';

                // Parse the fragment and extract messages
                const parser = new DOMParser();
                const doc = parser.parseFromString(html, 'text/html');

                // Get messages data and render them
                loadMessagesFromServer(conversationId);
            }

            // Update header info
            updateChatHeader(conversationId);

            // Mark as read and delivered
            markAsDelivered(conversationId);
            markAsRead(conversationId);

            // Show chat area on mobile
            showChatOnMobile();

            // Scroll to bottom
            scrollToBottom();
        })
        .catch(error => {
            console.error('Error loading conversation:', error);
        });
}

/**
 * Load messages from server via API
 */
function loadMessagesFromServer(conversationId) {
    // Find conversation data
    const conversation = conversationsData.find(c => c.id === conversationId);
    if (!conversation) return;

    // Update chat header with partner info
    const headerName = document.getElementById('chat-partner-name');
    const headerAvatar = document.querySelector('.chat-header-avatar');

    if (headerName) {
        headerName.textContent = conversation.userName;
    }

    if (headerAvatar && conversation.userProfilePicture) {
        headerAvatar.innerHTML = `<img src="${conversation.userProfilePicture}" alt="${conversation.userName}">`;
    }

    // Load messages via REST endpoint
    fetch('/message/get-messages/' + conversationId + '/1')
        .then(response => {
            if (response.ok) {
                return response.text();
            }
            throw new Error('Failed to load messages');
        })
        .then(html => {
            // For now, we'll just clear and wait for messages to come via WebSocket
            // In a real implementation, you'd parse the HTML fragment or use a JSON endpoint
            const messagesContainer = document.getElementById('chat-messages');
            if (messagesContainer) {
                messagesContainer.innerHTML = '<div class="chat-empty-state"><p>Loading messages...</p></div>';
            }
        })
        .catch(error => {
            console.error('Error loading messages:', error);
        });
}

/**
 * Send a message
 */
function sendMessage() {
    const input = document.getElementById('message-input');
    const content = input.value.trim();

    if (!content || !currentConversationId) {
        return;
    }

    // Send via WebSocket
    if (stompClient && stompClient.connected) {
        stompClient.send('/app/chat.send/' + currentConversationId, {}, content);
    } else {
        // Fallback to REST API
        sendMessageViaREST(currentConversationId, content);
    }

    // Clear input
    input.value = '';
    input.focus();
}

/**
 * Send message via REST API (fallback)
 */
function sendMessageViaREST(conversationId, content) {
    fetch('/message/send/' + conversationId, {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
        },
        body: content
    })
        .then(response => {
            if (response.ok) {
                console.log('Message sent via REST');
            } else {
                alert('Failed to send message');
            }
        })
        .catch(error => {
            console.error('Error sending message:', error);
            alert('Failed to send message');
        });
}

/**
 * Handle incoming message from WebSocket
 */
function handleIncomingMessage(messageDto) {
    currentMessages.push(messageDto);
    appendMessage(messageDto);
    scrollToBottom();

    // Mark as read if conversation is open
    if (currentConversationId === messageDto.conversationId) {
        markAsRead(currentConversationId);
    } else {
        // Update unread badge
        updateUnreadBadge(messageDto.conversationId, 1);
    }

    // Update conversation preview
    updateConversationPreview(messageDto);
}

/**
 * Append message to chat
 */
function appendMessage(messageDto) {
    const messagesContainer = document.getElementById('chat-messages');
    if (!messagesContainer) return;

    // Remove empty state if present
    const emptyState = messagesContainer.querySelector('.chat-empty-state');
    if (emptyState) {
        emptyState.remove();
    }

    // Create message element
    const messageEl = document.createElement('div');
    messageEl.className = 'chat-message ' + (messageDto.from ? 'sent' : 'received');
    messageEl.id = 'message-' + messageDto.id;

    const bubbleEl = document.createElement('div');
    bubbleEl.className = 'chat-message-bubble';

    const contentEl = document.createElement('div');
    contentEl.className = 'chat-message-content';
    contentEl.textContent = messageDto.content;

    const metaEl = document.createElement('div');
    metaEl.className = 'chat-message-meta';

    const timeEl = document.createElement('span');
    timeEl.className = 'chat-message-time';
    timeEl.textContent = formatTime(new Date(messageDto.date));

    metaEl.appendChild(timeEl);

    // Add status indicators for sent messages
    if (messageDto.from) {
        const statusEl = document.createElement('span');
        statusEl.className = 'chat-message-status';
        statusEl.id = 'status-' + messageDto.id;

        if (messageDto.read) {
            statusEl.innerHTML = '<i class="fas fa-check-double" style="color: #06b6d4;"></i>';
        } else if (messageDto.delivered) {
            statusEl.innerHTML = '<i class="fas fa-check-double"></i>';
        } else {
            statusEl.innerHTML = '<i class="fas fa-check"></i>';
        }

        metaEl.appendChild(statusEl);
    }

    bubbleEl.appendChild(contentEl);
    bubbleEl.appendChild(metaEl);

    // Add reactions container
    if (messageDto.reactions && messageDto.reactions.length > 0) {
        const reactionsEl = createReactionsElement(messageDto.reactions);
        bubbleEl.appendChild(reactionsEl);
    }

    // Add click handler for reactions
    bubbleEl.addEventListener('click', function (e) {
        if (e.target.closest('.chat-reaction')) {
            return; // Don't show picker if clicking on existing reaction
        }
        showReactionPicker(messageDto.id);
    });

    messageEl.appendChild(bubbleEl);
    messagesContainer.appendChild(messageEl);
}

/**
 * Create reactions element
 */
function createReactionsElement(reactions) {
    const reactionsEl = document.createElement('div');
    reactionsEl.className = 'chat-message-reactions';

    // Group reactions by emoji
    const reactionMap = new Map();
    reactions.forEach(reaction => {
        if (reactionMap.has(reaction.emoji)) {
            reactionMap.get(reaction.emoji).push(reaction);
        } else {
            reactionMap.set(reaction.emoji, [reaction]);
        }
    });

    // Create reaction elements
    reactionMap.forEach((users, emoji) => {
        const reactionEl = document.createElement('span');
        reactionEl.className = 'chat-reaction';
        reactionEl.innerHTML = `
            <span class="chat-reaction-emoji">${emoji}</span>
            <span class="chat-reaction-count">${users.length}</span>
        `;
        reactionsEl.appendChild(reactionEl);
    });

    return reactionsEl;
}

/**
 * Handle typing indicator
 */
function handleTypingIndicator(conversationId) {
    if (conversationId === currentConversationId) {
        const typingIndicator = document.getElementById('typing-indicator');
        if (typingIndicator) {
            typingIndicator.style.display = 'inline-flex';

            // Hide after 3 seconds
            setTimeout(() => {
                typingIndicator.style.display = 'none';
            }, 3000);
        }
    }
}

/**
 * Handle user typing
 */
function handleTyping() {
    if (!currentConversationId) return;

    // Clear previous timeout
    if (typingTimeout) {
        clearTimeout(typingTimeout);
    }

    // Send typing indicator
    if (stompClient && stompClient.connected) {
        stompClient.send('/app/chat.typing/' + currentConversationId, {}, '');
    }

    // Set new timeout
    typingTimeout = setTimeout(() => {
        // Stopped typing
    }, 2000);
}

/**
 * Handle message key press
 */
function handleMessageKeyPress(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}

/**
 * Mark conversation as read
 */
function markAsRead(conversationId) {
    if (stompClient && stompClient.connected) {
        stompClient.send('/app/chat.read/' + conversationId, {}, '');
    } else {
        // Fallback to REST API
        fetch('/message/read/' + conversationId, {
            method: 'POST'
        });
    }

    // Clear unread badge
    const badge = document.getElementById('unread-badge-' + conversationId);
    if (badge) {
        badge.style.display = 'none';
    }
}

/**
 * Mark conversation as delivered
 */
function markAsDelivered(conversationId) {
    if (stompClient && stompClient.connected) {
        stompClient.send('/app/chat.delivered/' + conversationId, {}, '');
    } else {
        // Fallback to REST API
        fetch('/message/delivered/' + conversationId, {
            method: 'POST'
        });
    }
}

/**
 * Handle read receipt
 */
function handleReadReceipt(statusDto) {
    const statusEl = document.getElementById('status-' + statusDto.messageId);
    if (statusEl) {
        if (statusDto.status === 'read') {
            statusEl.innerHTML = '<i class="fas fa-check-double" style="color: #06b6d4;"></i>';
        } else if (statusDto.status === 'delivered') {
            statusEl.innerHTML = '<i class="fas fa-check-double"></i>';
        }
    }
}

/**
 * Show reaction picker
 */
function showReactionPicker(messageId) {
    reactionMessageId = messageId;
    const modal = document.getElementById('reaction-picker-modal');
    if (modal) {
        modal.classList.add('is-active');
    }
}

/**
 * Close reaction picker
 */
function closeReactionPicker() {
    const modal = document.getElementById('reaction-picker-modal');
    if (modal) {
        modal.classList.remove('is-active');
    }
    reactionMessageId = null;
}

/**
 * Add reaction to message
 */
function addReactionToMessage(emoji) {
    if (!reactionMessageId) return;

    if (stompClient && stompClient.connected) {
        stompClient.send('/app/chat.react/' + reactionMessageId, {}, emoji);
    } else {
        // Fallback to REST API
        fetch('/message/' + reactionMessageId + '/react', {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain',
            },
            body: emoji
        });
    }

    closeReactionPicker();
}

/**
 * Handle reaction update from WebSocket
 */
function handleReactionUpdate(reactionDto) {
    const messageEl = document.getElementById('message-' + reactionDto.messageId);
    if (!messageEl) return;

    // Find or create reactions container
    let reactionsEl = messageEl.querySelector('.chat-message-reactions');
    if (!reactionsEl) {
        reactionsEl = document.createElement('div');
        reactionsEl.className = 'chat-message-reactions';
        messageEl.querySelector('.chat-message-bubble').appendChild(reactionsEl);
    }

    // Update or add reaction
    // For simplicity, reload the entire reactions - in production, update incrementally
    updateMessageReactions(reactionDto.messageId);
}

/**
 * Handle reaction removal from WebSocket
 */
function handleReactionRemoval(reactionDto) {
    updateMessageReactions(reactionDto.messageId);
}

/**
 * Update message reactions (reload from server)
 */
function updateMessageReactions(messageId) {
    // In a real implementation, you'd fetch updated reactions from the server
    // For now, we'll just rely on the WebSocket updates
}

/**
 * Show emoji picker
 */
function toggleEmojiPicker() {
    const modal = document.getElementById('emoji-picker-modal');
    if (modal) {
        modal.classList.toggle('is-active');
    }
}

/**
 * Close emoji picker
 */
function closeEmojiPicker() {
    const modal = document.getElementById('emoji-picker-modal');
    if (modal) {
        modal.classList.remove('is-active');
    }
}

/**
 * Insert emoji into message input
 */
function insertEmoji(emoji) {
    const input = document.getElementById('message-input');
    if (input) {
        input.value += emoji;
        input.focus();
    }
    closeEmojiPicker();
}

/**
 * Open conversation (from sidebar click)
 */
function openConversation(conversationId) {
    loadConversation(conversationId);
}

/**
 * Update chat header
 */
function updateChatHeader(conversationId) {
    const conversation = conversationsData.find(c => c.id === conversationId);
    if (!conversation) return;

    const headerName = document.getElementById('chat-partner-name');
    const headerAvatar = document.querySelector('.chat-header-avatar');

    if (headerName) {
        headerName.textContent = conversation.userName;
    }

    if (headerAvatar && conversation.userProfilePicture) {
        headerAvatar.innerHTML = `<img src="${conversation.userProfilePicture}" alt="${conversation.userName}">`;
    }
}

/**
 * Update unread badge
 */
function updateUnreadBadge(conversationId, increment) {
    const badge = document.getElementById('unread-badge-' + conversationId);
    if (badge) {
        let count = parseInt(badge.textContent) || 0;
        count += increment;
        badge.textContent = count;
        badge.style.display = count > 0 ? 'inline-block' : 'none';
    }
}

/**
 * Update conversation preview
 */
function updateConversationPreview(messageDto) {
    const conversationItem = document.getElementById('conversation-' + messageDto.conversationId);
    if (!conversationItem) return;

    const preview = conversationItem.querySelector('.chat-conversation-preview');
    if (preview) {
        preview.textContent = messageDto.content;
    }

    const time = conversationItem.querySelector('.chat-conversation-time');
    if (time) {
        time.textContent = formatTime(new Date(messageDto.date));
    }
}

/**
 * Scroll to bottom of messages
 */
function scrollToBottom() {
    const messagesContainer = document.getElementById('chat-messages');
    if (messagesContainer) {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
}

/**
 * Format time
 */
function formatTime(date) {
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return hours + ':' + minutes;
}

/**
 * Mobile: Show chat area
 */
function showChatOnMobile() {
    if (window.innerWidth <= 768) {
        const sidebar = document.getElementById('chat-sidebar');
        if (sidebar) {
            sidebar.classList.add('hidden');
        }
    }
}

/**
 * Mobile: Close chat and show sidebar
 */
function closeChatOnMobile() {
    if (window.innerWidth <= 768) {
        const sidebar = document.getElementById('chat-sidebar');
        if (sidebar) {
            sidebar.classList.remove('hidden');
        }
    }
}
