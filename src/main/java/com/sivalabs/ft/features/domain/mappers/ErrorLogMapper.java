package com.sivalabs.ft.features.domain.mappers;

import com.sivalabs.ft.features.domain.dtos.ErrorLogDto;
import com.sivalabs.ft.features.domain.entities.ErrorLog;
import org.springframework.stereotype.Component;

@Component
public class ErrorLogMapper {

    public ErrorLogDto toDto(ErrorLog errorLog) {
        return new ErrorLogDto(
                errorLog.getId(),
                errorLog.getTimestamp(),
                errorLog.getErrorType(),
                errorLog.getErrorMessage(),
                errorLog.getStackTrace(),
                errorLog.getEventPayload(),
                errorLog.getUserId(),
                errorLog.getResolved());
    }
}
