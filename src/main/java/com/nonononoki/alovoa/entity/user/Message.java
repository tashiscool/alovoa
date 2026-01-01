package com.nonononoki.alovoa.entity.user;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.component.TextEncryptorConverter;
import com.nonononoki.alovoa.entity.User;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
public class Message {

	@JsonIgnore
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@JsonIgnore
	@ManyToOne
	private Conversation conversation;

	@JsonIgnore
	@ManyToOne
	@JoinColumn
	private User userFrom;

	@JsonIgnore
	@ManyToOne
	@JoinColumn
	private User userTo;

	@Convert(converter = TextEncryptorConverter.class)
	@Column(columnDefinition = "mediumtext", updatable = false)

	private String content;

	private Date date;

	private boolean allowedFormatting = false;

	@Column(name = "read_at")
	private Date readAt;

	@Column(name = "delivered_at")
	private Date deliveredAt;

	@OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<MessageReaction> reactions = new ArrayList<>();

	public boolean isRead() {
		return readAt != null;
	}

	public boolean isDelivered() {
		return deliveredAt != null;
	}
}