package com.nonononoki.alovoa.entity.user;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import com.nonononoki.alovoa.entity.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class UserNotification {

	@Transient
	public static final String USER_LIKE = "USER_LIKE";
	@Transient
	public static final String INTERVENTION_GENTLE = "INTERVENTION_GENTLE";
	@Transient
	public static final String INTERVENTION_SUPPORT = "INTERVENTION_SUPPORT";
	@Transient
	public static final String INTERVENTION_CRISIS = "INTERVENTION_CRISIS";
	@Transient
	public static final String ACCOUNT_PAUSE_NOTICE = "ACCOUNT_PAUSE_NOTICE";
	@Transient
	public static final String RECOVERY_WELCOME = "RECOVERY_WELCOME";
	@Transient
	public static final String RESOURCE_OFFER = "RESOURCE_OFFER";

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	private User userFrom;

	@ManyToOne
	@JoinColumn
	private User userTo;

	private String content;

	@Column(name = "notification_type", length = 50)
	private String notificationType = USER_LIKE;

	private String message;

	private Date date;

	@Column(name = "read_status")
	private Boolean readStatus = false;

	@Column(name = "action_taken", length = 50)
	private String actionTaken;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "action_taken_at")
	private Date actionTakenAt;

}