package com.nonononoki.alovoa.model;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusDto {
	private Long messageId;
	private Long conversationId;
	private String status; // "sent", "delivered", "read"
	private Date timestamp;
	private Long userId; // who triggered the status update
}
