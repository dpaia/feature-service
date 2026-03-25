package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.EmailDeliveryFailureDto;
import com.sivalabs.ft.features.domain.entities.EmailDeliveryFailure;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.EmailDeliveryFailureMapper;
import com.sivalabs.ft.features.domain.models.NotificationEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryFailureService {

    private final EmailDeliveryFailureRepository repository;
    private final EmailDeliveryFailureMapper mapper;

    public EmailDeliveryFailureService(EmailDeliveryFailureRepository repository, EmailDeliveryFailureMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailure(
            UUID notificationId, String recipientEmail, NotificationEventType eventType, String errorMessage) {
        EmailDeliveryFailure failure = new EmailDeliveryFailure();
        failure.setNotificationId(notificationId);
        failure.setRecipientEmail(recipientEmail);
        failure.setEventType(eventType);
        failure.setErrorMessage(errorMessage);
        failure.setFailedAt(Instant.now());
        repository.save(failure);
    }

    @Transactional(readOnly = true)
    public Page<EmailDeliveryFailureDto> getFailures(LocalDate date, Pageable pageable) {
        if (date != null) {
            Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            return repository
                    .findByFailedAtBetweenOrderByFailedAtDesc(start, end, pageable)
                    .map(mapper::toDto);
        }
        return repository.findAllOrderByFailedAtDesc(pageable).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public EmailDeliveryFailureDto getFailureById(UUID id) {
        return repository
                .findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("EmailDeliveryFailure not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<EmailDeliveryFailureDto> getFailuresByNotificationId(UUID notificationId) {
        return repository.findByNotificationIdOrderByFailedAtDesc(notificationId).stream()
                .map(mapper::toDto)
                .toList();
    }
}
