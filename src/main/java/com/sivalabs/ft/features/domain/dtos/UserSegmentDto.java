package com.sivalabs.ft.features.domain.dtos;

import java.util.Map;

/**
 * DTO representing a user segment definition.
 * Segments group users based on common characteristics or behavior patterns.
 *
 * @param segmentId Unique identifier for the segment
 * @param segmentName Display name for the segment
 * @param description Detailed description of the segment
 * @param criteria Map of criteria defining segment membership (e.g., {"device": "mobile", "region": "US"})
 */
public record UserSegmentDto(String segmentId, String segmentName, String description, Map<String, String> criteria) {}
