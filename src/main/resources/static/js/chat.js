/**
 * AURA Chat - WebSocket Client (Production-Ready)
 * Real-time messaging with SockJS and STOMP
 *
 * Fixes applied:
 * 1. JSON endpoint for messages (no more HTML parsing)
 * 2. Explicit senderUserId for isMine checks
 * 3. Safe reconnect (no duplicate subscriptions)
 * 4. Full reaction updates
 * 5. Proper unread badge reset
 * 6. Optimistic UI for sent messages
 * 7. Scroll-aware mark-as-read
 * 8. Message deduplication and ordering
 */

// Global state
let stompClient = null;
let currentConversationId = selectedConversationId;
let currentMessages = [];
let renderedMessageIds = new Set();  // For deduplication
let typingTimeout = null;
let reactionMessageId = null;
let isConnecting = false;  // Prevent duplicate connection attempts
let reconnectTimer = null;
let tempMessageCounter = 0;  // For optimistic UI temp IDs

// Initialize chat on page load
document.addEventListener('DOMContentLoaded', function () {
    initializeWebSocket();

    // Load initial conversation if one is selected
    if (currentConversationId) {
        loadConversation(currentConversationId);
    }

    // Setup scroll listener for mark-as-read
    setupScrollListener();
});

/**
 * Check if a message is from the current user
 * Uses explicit senderUserId instead of ambiguous 'from' boolean
 */
function isMine(msg) {
    // Prefer senderUserId if available, fallback to 'from' boolean
    if (msg.senderUserId !== undefined) {
        return msg.senderUserId === currentUserId;
    }
    return msg.from === true;
}

/**
 * Initialize WebSocket connection with reconnect safety
 */
function initializeWebSocket() {
    // Prevent duplicate connection attempts
    if (isConnecting) {
        console.log('WebSocket connection already in progress');
        return;
    }

    // Clear any pending reconnect timer
    if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }

    // Disconnect existing client if any
    if (stompClient && stompClient.connected) {
        try {
            stompClient.disconnect();
        } catch (e) {
            console.log('Error disconnecting existing client:', e);
        }
    }

    isConnecting = true;

    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // Configure STOMP client
    stompClient.debug = function (str) {
        // Disable STOMP debug logging in production
        // console.log(str);
    };

    stompClient.connect({}, function (frame) {
        console.log('WebSocket connected: ' + frame);
        isConnecting = false;

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
        isConnecting = false;

        // Schedule reconnect with backoff (only one timer active)
        if (!reconnectTimer) {
            reconnectTimer = setTimeout(function() {
                reconnectTimer = null;
                initializeWebSocket();
            }, 5000);
        }
    });
}

/**
 * Load conversation messages using JSON endpoint
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

    // Clear current messages and rendered IDs
    currentMessages = [];
    renderedMessageIds.clear();

    const messagesContainer = document.getElementById('chat-messages');
    if (messagesContainer) {
        messagesContainer.innerHTML = '<div class="chat-loading"><i class="fas fa-spinner fa-spin"></i> Loading messages...</div>';
    }

    // Update header info
    updateChatHeader(conversationId);

    // Load messages via JSON API
    fetch('/message/api/v1/messages/' + conversationId + '?page=1')
        .then(response => {
            if (!response.ok) throw new Error('Failed to load messages');
            return response.json();
        })
        .then(data => {
            if (messagesContainer) {
                messagesContainer.innerHTML = '';
            }

            // Render messages from oldest to newest
            const messages = data.messages || [];
            messages.forEach(msg => {
                if (!renderedMessageIds.has(msg.id)) {
                    currentMessages.push(msg);
                    renderedMessageIds.add(msg.id);
                    appendMessage(msg);
                }
            });

            // Show empty state if no messages
            if (messages.length === 0 && messagesContainer) {
                messagesContainer.innerHTML = '<div class="chat-empty-state"><p>No messages yet. Say hello!</p></div>';
            }

            // Mark as delivered and read
            markAsDelivered(conversationId);
            markAsReadIfAtBottom();

            // Show chat area on mobile
            showChatOnMobile();

            // Scroll to bottom
            scrollToBottom();
        })
        .catch(error => {
            console.error('Error loading conversation:', error);
            if (messagesContainer) {
                messagesContainer.innerHTML = '<div class="chat-error"><p>Failed to load messages. <a href="javascript:loadConversation(' + conversationId + ')">Retry</a></p></div>';
            }
        });
}

/**
 * Send a message with optimistic UI
 */
function sendMessage() {
    const input = document.getElementById('message-input');
    const content = input.value.trim();

    if (!content || !currentConversationId) {
        return;
    }

    // Generate temp ID for optimistic UI
    const clientTempId = 'temp-' + (++tempMessageCounter) + '-' + Date.now();

    // Create optimistic message DTO
    const optimisticMsg = {
        id: clientTempId,
        conversationId: currentConversationId,
        content: content,
        date: new Date().toISOString(),
        senderUserId: currentUserId,
        from: true,
        delivered: false,
        read: false,
        isPending: true,  // Mark as pending for styling
        clientTempId: clientTempId
    };

    // Append optimistically
    appendMessage(optimisticMsg);
    scrollToBottom();

    // Clear input immediately for better UX
    input.value = '';
    input.focus();

    // Send via WebSocket
    if (stompClient && stompClient.connected) {
        stompClient.send('/app/chat.send/' + currentConversationId, {}, content);
    } else {
        // Fallback to REST API
        sendMessageViaREST(currentConversationId, content, clientTempId);
    }

    // Update conversation preview immediately
    updateConversationPreview({
        conversationId: currentConversationId,
        content: content,
        date: optimisticMsg.date
    });
}

/**
 * Send message via REST API (fallback)
 */
function sendMessageViaREST(conversationId, content, clientTempId) {
    fetch('/message/send/' + conversationId, {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
        },
        body: content
    })
        .then(response => {
            if (!response.ok) {
                // Mark optimistic message as failed
                markOptimisticMessageFailed(clientTempId);
                alert('Failed to send message');
            }
        })
        .catch(error => {
            console.error('Error sending message:', error);
            markOptimisticMessageFailed(clientTempId);
            alert('Failed to send message');
        });
}

/**
 * Mark an optimistic message as failed
 */
function markOptimisticMessageFailed(clientTempId) {
    const msgEl = document.getElementById('message-' + clientTempId);
    if (msgEl) {
        msgEl.classList.add('failed');
        const statusEl = msgEl.querySelector('.chat-message-status');
        if (statusEl) {
            statusEl.innerHTML = '<i class="fas fa-exclamation-circle" style="color: #e74c3c;"></i>';
        }
    }
}

/**
 * Handle incoming message from WebSocket
 */
function handleIncomingMessage(messageDto) {
    // Check if this is an echo of our optimistic message
    if (messageDto.clientTempId) {
        // Replace optimistic message with real one
        const tempEl = document.getElementById('message-' + messageDto.clientTempId);
        if (tempEl) {
            tempEl.id = 'message-' + messageDto.id;
            tempEl.classList.remove('pending');
            // Update status to sent
            const statusEl = tempEl.querySelector('.chat-message-status');
            if (statusEl) {
                statusEl.innerHTML = '<i class="fas fa-check"></i>';
            }
            // Update in currentMessages
            const idx = currentMessages.findIndex(m => m.clientTempId === messageDto.clientTempId);
            if (idx >= 0) {
                currentMessages[idx] = messageDto;
            }
            renderedMessageIds.add(messageDto.id);
            return;
        }
    }

    // Deduplication: don't render if already rendered
    if (renderedMessageIds.has(messageDto.id)) {
        // Just update the existing message (e.g., for status updates)
        updateExistingMessage(messageDto);
        return;
    }

    // Add to tracking
    renderedMessageIds.add(messageDto.id);
    currentMessages.push(messageDto);

    // Insert in correct position (by date)
    appendMessageSorted(messageDto);

    // Mark as read only if at bottom and window focused
    if (currentConversationId === messageDto.conversationId) {
        markAsReadIfAtBottom();
    } else {
        // Update unread badge
        updateUnreadBadge(messageDto.conversationId, 1);
    }

    // Update conversation preview
    updateConversationPreview(messageDto);
}

/**
 * Append message maintaining chronological order
 */
function appendMessageSorted(messageDto) {
    const messagesContainer = document.getElementById('chat-messages');
    if (!messagesContainer) return;

    // Remove empty state if present
    const emptyState = messagesContainer.querySelector('.chat-empty-state, .chat-loading');
    if (emptyState) {
        emptyState.remove();
    }

    const msgDate = new Date(messageDto.date);
    const messageEl = createMessageElement(messageDto);

    // Find correct insertion point
    const existingMessages = messagesContainer.querySelectorAll('.chat-message');
    let inserted = false;

    for (let i = existingMessages.length - 1; i >= 0; i--) {
        const existingId = existingMessages[i].id.replace('message-', '');
        const existingMsg = currentMessages.find(m => String(m.id) === existingId);
        if (existingMsg) {
            const existingDate = new Date(existingMsg.date);
            if (msgDate >= existingDate) {
                existingMessages[i].after(messageEl);
                inserted = true;
                break;
            }
        }
    }

    if (!inserted) {
        messagesContainer.prepend(messageEl);
    }

    scrollToBottom();
}

/**
 * Append message (simple append to end)
 */
function appendMessage(messageDto) {
    const messagesContainer = document.getElementById('chat-messages');
    if (!messagesContainer) return;

    // Remove empty state if present
    const emptyState = messagesContainer.querySelector('.chat-empty-state, .chat-loading');
    if (emptyState) {
        emptyState.remove();
    }

    const messageEl = createMessageElement(messageDto);
    messagesContainer.appendChild(messageEl);
}

/**
 * Create message DOM element
 */
function createMessageElement(messageDto) {
    const messageEl = document.createElement('div');
    const mine = isMine(messageDto);
    messageEl.className = 'chat-message ' + (mine ? 'sent' : 'received');
    if (messageDto.isPending) {
        messageEl.className += ' pending';
    }
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
    if (mine) {
        const statusEl = document.createElement('span');
        statusEl.className = 'chat-message-status';
        statusEl.id = 'status-' + messageDto.id;

        if (messageDto.isPending) {
            statusEl.innerHTML = '<i class="fas fa-clock" style="opacity: 0.5;"></i>';
        } else if (messageDto.read) {
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
    return messageEl;
}

/**
 * Update an existing message element
 */
function updateExistingMessage(messageDto) {
    const msgEl = document.getElementById('message-' + messageDto.id);
    if (!msgEl) return;

    // Update status
    const statusEl = msgEl.querySelector('.chat-message-status');
    if (statusEl && isMine(messageDto)) {
        if (messageDto.read) {
            statusEl.innerHTML = '<i class="fas fa-check-double" style="color: #06b6d4;"></i>';
        } else if (messageDto.delivered) {
            statusEl.innerHTML = '<i class="fas fa-check-double"></i>';
        } else {
            statusEl.innerHTML = '<i class="fas fa-check"></i>';
        }
    }

    // Update reactions
    const bubbleEl = msgEl.querySelector('.chat-message-bubble');
    if (messageDto.reactions) {
        let reactionsEl = bubbleEl.querySelector('.chat-message-reactions');
        if (reactionsEl) {
            reactionsEl.remove();
        }
        if (messageDto.reactions.length > 0) {
            reactionsEl = createReactionsElement(messageDto.reactions);
            bubbleEl.appendChild(reactionsEl);
        }
    }
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
 * Setup scroll listener for smart mark-as-read
 */
function setupScrollListener() {
    const messagesContainer = document.getElementById('chat-messages');
    if (messagesContainer) {
        messagesContainer.addEventListener('scroll', function() {
            markAsReadIfAtBottom();
        });
    }

    // Also mark as read when window regains focus
    window.addEventListener('focus', function() {
        if (currentConversationId) {
            markAsReadIfAtBottom();
        }
    });
}

/**
 * Check if scrolled to bottom (with threshold)
 */
function isAtBottom() {
    const messagesContainer = document.getElementById('chat-messages');
    if (!messagesContainer) return true;

    const threshold = 50; // pixels from bottom
    return (messagesContainer.scrollHeight - messagesContainer.scrollTop - messagesContainer.clientHeight) < threshold;
}

/**
 * Mark conversation as read only if at bottom and window focused
 */
function markAsReadIfAtBottom() {
    if (!currentConversationId) return;
    if (!document.hasFocus()) return;
    if (!isAtBottom()) return;

    markAsRead(currentConversationId);
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

    // Clear unread badge properly (set to 0, not just hide)
    const badge = document.getElementById('unread-badge-' + conversationId);
    if (badge) {
        badge.textContent = '0';
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
    // Fetch fresh reactions from server to get full list
    fetchAndUpdateReactions(reactionDto.messageId);
}

/**
 * Handle reaction removal from WebSocket
 */
function handleReactionRemoval(reactionDto) {
    // Fetch fresh reactions from server to get full list
    fetchAndUpdateReactions(reactionDto.messageId);
}

/**
 * Fetch and update reactions for a message
 */
function fetchAndUpdateReactions(messageId) {
    fetch('/message/api/v1/messages/' + messageId + '/reactions')
        .then(response => response.json())
        .then(reactions => {
            const messageEl = document.getElementById('message-' + messageId);
            if (!messageEl) return;

            const bubbleEl = messageEl.querySelector('.chat-message-bubble');
            if (!bubbleEl) return;

            // Remove existing reactions
            const existingReactions = bubbleEl.querySelector('.chat-message-reactions');
            if (existingReactions) {
                existingReactions.remove();
            }

            // Add new reactions if any
            if (reactions && reactions.length > 0) {
                const reactionsEl = createReactionsElement(reactions);
                bubbleEl.appendChild(reactionsEl);
            }

            // Update in currentMessages array
            const msgIdx = currentMessages.findIndex(m => m.id === messageId);
            if (msgIdx >= 0) {
                currentMessages[msgIdx].reactions = reactions;
            }
        })
        .catch(error => {
            console.error('Error fetching reactions:', error);
        });
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
