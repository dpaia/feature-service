package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.ActionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "feature_usage")
public class FeatureUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "feature_usage_id_gen")
    @SequenceGenerator(name = "feature_usage_id_gen", sequenceName = "feature_usage_id_seq")
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 255) @NotNull @Column(name = "user_id", nullable = false)
    private String userId;

    @Size(max = 50) @Column(name = "feature_code", length = 50)
    private String featureCode;

    @Size(max = 50) @Column(name = "product_code", length = 50)
    private String productCode;

    @Size(max = 50) @Column(name = "release_code", length = 50)
    private String releaseCode;

    @NotNull @Column(name = "action_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @NotNull @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "context", length = Integer.MAX_VALUE)
    private String context;

    @Size(max = 45) @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Size(max = 500) @Column(name = "user_agent", length = 500)
    private String userAgent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getReleaseCode() {
        return releaseCode;
    }

    public void setReleaseCode(String releaseCode) {
        this.releaseCode = releaseCode;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
