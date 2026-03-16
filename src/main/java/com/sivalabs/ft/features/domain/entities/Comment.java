package com.sivalabs.ft.features.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

@Data
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comment_id_gen")
    @SequenceGenerator(name = "comment_id_gen", sequenceName = "comment_id_seq")
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "updated_by")
    private String updatedBy;

    @NotNull @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Comment() {}

    public Comment(Feature feature, String createdBy, String content, String updatedBy) {
        this.feature = feature;
        this.createdBy = createdBy;
        this.content = content;
        this.updatedBy = updatedBy;
        this.createdAt = Instant.now();
    }
<empty>
}
