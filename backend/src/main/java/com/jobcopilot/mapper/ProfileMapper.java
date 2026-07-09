package com.jobcopilot.mapper;

import com.jobcopilot.dto.profile.ProfileRequest;
import com.jobcopilot.dto.profile.ProfileResponse;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.util.JsonListUtil;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;

/**
 * Maps between {@link Profile} entities and profile DTOs, converting JSON list columns
 * to/from {@code List<String>}.
 *
 * <p>The builder is disabled so MapStruct constructs {@link Profile} via its no-arg constructor
 * and setters. That is required because the audit fields ({@code createdAt}/{@code updatedAt})
 * are inherited from {@code Auditable} and are therefore not present on Lombok's non-inheriting
 * {@code @Builder}; setters, however, are inherited and available.</p>
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ProfileMapper {

    @Mapping(target = "titles", source = "titles", qualifiedByName = "jsonToList")
    @Mapping(target = "locations", source = "locations", qualifiedByName = "jsonToList")
    @Mapping(target = "keywords", source = "keywords", qualifiedByName = "jsonToList")
    @Mapping(target = "excludedCompanies", source = "excludedCompanies", qualifiedByName = "jsonToList")
    ProfileResponse toResponse(Profile entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "titles", source = "titles", qualifiedByName = "listToJson")
    @Mapping(target = "locations", source = "locations", qualifiedByName = "listToJson")
    @Mapping(target = "keywords", source = "keywords", qualifiedByName = "listToJson")
    @Mapping(target = "excludedCompanies", source = "excludedCompanies", qualifiedByName = "listToJson")
    Profile toEntity(ProfileRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "titles", source = "titles", qualifiedByName = "listToJson")
    @Mapping(target = "locations", source = "locations", qualifiedByName = "listToJson")
    @Mapping(target = "keywords", source = "keywords", qualifiedByName = "listToJson")
    @Mapping(target = "excludedCompanies", source = "excludedCompanies", qualifiedByName = "listToJson")
    void updateEntity(ProfileRequest request, @MappingTarget Profile entity);

    @Named("jsonToList")
    default List<String> jsonToList(String json) {
        return JsonListUtil.fromJson(json);
    }

    @Named("listToJson")
    default String listToJson(List<String> values) {
        return JsonListUtil.toJson(values);
    }
}
