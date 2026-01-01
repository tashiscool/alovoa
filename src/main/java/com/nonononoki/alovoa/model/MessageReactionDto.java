package com.nonononoki.alovoa.model;

import java.util.ArrayList;
import java.util.List;

import com.nonononoki.alovoa.entity.user.MessageReaction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageReactionDto {

	private Long messageId;
	private String emoji;
	private Long userId;
	private String userFirstName;

	public static MessageReactionDto reactionToDto(MessageReaction reaction) {
		MessageReactionDto dto = new MessageReactionDto();
		dto.setMessageId(reaction.getMessage().getId());
		dto.setEmoji(reaction.getEmoji());
		dto.setUserId(reaction.getUser().getId());
		dto.setUserFirstName(reaction.getUser().getFirstName());
		return dto;
	}

	public static List<MessageReactionDto> reactionsToDtos(List<MessageReaction> reactions) {
		List<MessageReactionDto> dtos = new ArrayList<>();
		for (MessageReaction reaction : reactions) {
			dtos.add(reactionToDto(reaction));
		}
		return dtos;
	}
}
