package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.ReactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "feature_reactions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"feature_id", "user_id"})
})
public class FeatureReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "feature_reactions_id_gen")
    @SequenceGenerator(name = "feature_reactions_id_gen", sequenceName = "feature_reaction_id_seq")
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    @NotNull
    private Feature feature;

    @Size(max = 255)
    @NotNull
    @Column(name = "user_id", nullable = false)
    private String userId;

    @NotNull
    @Column(name = "reaction_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReactionType reactionType;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public FeatureReaction() {
    }

    public FeatureReaction(Feature feature, String userId, ReactionType reactionType) {
        this.feature = feature;
        this.userId = userId;
        this.reactionType = reactionType;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Feature getFeature() {
        return feature;
    }

    public void setFeature(Feature feature) {
        this.feature = feature;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ReactionType getReactionType() {
        return reactionType;
    }

    public void setReactionType(ReactionType reactionType) {
        this.reactionType = reactionType;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}