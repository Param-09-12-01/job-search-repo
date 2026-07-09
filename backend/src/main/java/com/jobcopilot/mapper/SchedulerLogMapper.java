package com.jobcopilot.mapper;

import com.jobcopilot.dto.scheduler.SchedulerLogResponse;
import com.jobcopilot.entity.SchedulerLog;
import org.mapstruct.Mapper;

/**
 * Maps {@link SchedulerLog} entities to response DTOs.
 */
@Mapper(componentModel = "spring")
public interface SchedulerLogMapper {

    SchedulerLogResponse toResponse(SchedulerLog entity);
}
