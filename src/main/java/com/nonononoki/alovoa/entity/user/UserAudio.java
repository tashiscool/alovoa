package com.nonononoki.alovoa.entity.user;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
public class UserAudio {

	@JsonIgnore
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique=true)
	private UUID uuid;
	
	@JsonIgnore
	@OneToOne
	@EqualsAndHashCode.Exclude
	private User user;

	@Column(name = "s3_key")
	@EqualsAndHashCode.Exclude
	private String s3Key;

	private String binMime;

}
