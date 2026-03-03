package com.sivalabs.ft.features.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "milestones")
public class Milestone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String releaseCode;

    @Column(nullable = false)
    private LocalDate targetDate;

    private int resolvedFeatures;
    private int totalFeatures;

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getReleaseCode() {
        return releaseCode;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public int getResolvedFeatures() {
        return resolvedFeatures;
    }

    public int getTotalFeatures() {
        return totalFeatures;
    }
}
