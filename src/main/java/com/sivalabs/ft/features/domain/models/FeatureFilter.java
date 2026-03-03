package com.sivalabs.ft.features.domain.models;

public class FeatureFilter {
    private String productCode;
    private String releaseCode;
    private FeatureStatus status;
    private String assignedTo;

    public FeatureFilter(String productCode, String releaseCode, FeatureStatus status, String assignedTo) {
        this.productCode = productCode;
        this.releaseCode = releaseCode;
        this.status = status;
        this.assignedTo = assignedTo;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getReleaseCode() {
        return releaseCode;
    }

    public FeatureStatus getStatus() {
        return status;
    }

    public String getAssignedTo() {
        return assignedTo;
    }
}
