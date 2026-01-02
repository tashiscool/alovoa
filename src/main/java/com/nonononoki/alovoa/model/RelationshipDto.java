package com.nonononoki.alovoa.model;

import com.nonononoki.alovoa.entity.UserRelationship.RelationshipStatus;
import com.nonononoki.alovoa.entity.UserRelationship.RelationshipType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * DTO for relationship information.
 */
@Getter
@Setter
@Builder
public class RelationshipDto {
    private UUID uuid;
    private RelationshipType type;
    private String typeDisplayName;
    private RelationshipStatus status;
    private UUID partnerUuid;
    private String partnerName;
    private boolean partnerHasProfilePicture;
    private boolean isInitiator;
    private Boolean isPublic;
    private Date createdAt;
    private Date confirmedAt;
    private Date anniversaryDate;
}
