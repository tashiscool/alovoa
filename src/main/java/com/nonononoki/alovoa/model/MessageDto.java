package com.nonononoki.alovoa.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Message;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageDto {
	private long id;
	private Date date;
	private String content;
	private boolean from;
	private boolean allowedFormatting;
	private Date readAt;
	private Date deliveredAt;
	private boolean read;
	private boolean delivered;
	private List<MessageReactionDto> reactions;
	
	public static MessageDto messageToDto(Message message, User user) {
		MessageDto dto = new MessageDto();
		dto.setId(message.getId());
		dto.setContent(message.getContent());
		dto.setDate(message.getDate());
		dto.setFrom(message.getUserFrom().equals(user));
		dto.setAllowedFormatting(message.isAllowedFormatting());
		dto.setReadAt(message.getReadAt());
		dto.setDeliveredAt(message.getDeliveredAt());
		dto.setRead(message.isRead());
		dto.setDelivered(message.isDelivered());
		dto.setReactions(MessageReactionDto.reactionsToDtos(message.getReactions()));
		return dto;
	}
	
	public static List<MessageDto> messagesToDtos(List<Message> messages, User user) {
		List<MessageDto> dtos = new ArrayList<>();
		for(int i = 0; i < messages.size(); i++) {
			dtos.add(MessageDto.messageToDto(messages.get(i), user));
		}
		return dtos;
	}
}
