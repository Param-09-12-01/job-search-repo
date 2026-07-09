package com.jobcopilot.mapper;

import com.jobcopilot.dto.notification.NotificationResponse;
import com.jobcopilot.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link Notification} entities to response DTOs.
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "postingId", source = "posting.id")
    NotificationResponse toResponse(Notification entity);
}
