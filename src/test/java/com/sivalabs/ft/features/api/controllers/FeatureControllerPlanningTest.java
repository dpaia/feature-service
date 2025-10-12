package com.sivalabs.ft.features.api.controllers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import com.sivalabs.ft.features.api.models.UpdateFeaturePayload;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeatureControllerPlanningTest extends AbstractIT {

    @Test
    @WithMockOAuth2User
    void shouldUpdateFeatureWithPlanningFields() {
        // Given - using existing feature from test data
        String featureCode = "BOOKSTORE-100";
        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                "Updated Feature with Planning",
                "Updated description with planning fields",
                null,
                "john.doe@example.com",
                FeatureStatus.IN_PROGRESS,
                LocalDate.of(2024, 12, 31),
                LocalDate.of(2024, 12, 25),
                FeaturePlanningStatus.IN_PROGRESS,
                "jane.smith@example.com",
                "Waiting for dependency resolution");

        // When
        given().contentType(ContentType.JSON)
                .body(payload)
                .when()
                .put("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value());

        // Then - Verify the feature was updated with planning fields
        given().when()
                .get("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("code", equalTo(featureCode))
                .body("title", equalTo("Updated Feature with Planning"))
                .body("description", equalTo("Updated description with planning fields"))
                .body("status", equalTo("IN_PROGRESS"))
                .body("assignedTo", equalTo("john.doe@example.com"))
                .body("plannedCompletionDate", equalTo("2024-12-31"))
                .body("actualCompletionDate", equalTo("2024-12-25"))
                .body("featurePlanningStatus", equalTo("IN_PROGRESS"))
                .body("featureOwner", equalTo("jane.smith@example.com"))
                .body("blockageReason", equalTo("Waiting for dependency resolution"));
    }

    @Test
    @WithMockOAuth2User
    void shouldUpdateFeatureWithNullPlanningFields() {
        // Given - using existing feature from test data
        String featureCode = "BOOKSTORE-101";
        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                "Feature without Planning",
                "Description without planning fields",
                null,
                "admin@example.com",
                FeatureStatus.NEW,
                null,
                null,
                null,
                null,
                null);

        // When
        given().contentType(ContentType.JSON)
                .body(payload)
                .when()
                .put("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value());

        // Then - Verify the feature was updated with null planning fields
        given().when()
                .get("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("code", equalTo(featureCode))
                .body("title", equalTo("Feature without Planning"))
                .body("status", equalTo("NEW"))
                .body("assignedTo", equalTo("admin@example.com"))
                .body("plannedCompletionDate", nullValue())
                .body("featurePlanningStatus", nullValue())
                .body("featureOwner", nullValue())
                .body("blockageReason", nullValue())
                .body("actualCompletionDate", nullValue());
    }

    @Test
    @WithMockOAuth2User
    void shouldUpdateFeatureWithCompletionDates() {
        // Given - using existing feature from test data
        String featureCode = "BOOKSTORE-102";
        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                "Completed Feature",
                "Feature that is completed",
                null,
                "developer@example.com",
                FeatureStatus.RELEASED,
                LocalDate.of(2024, 11, 30),
                LocalDate.of(2024, 11, 28),
                FeaturePlanningStatus.DONE,
                "project.manager@example.com",
                null);

        // When
        given().contentType(ContentType.JSON)
                .body(payload)
                .when()
                .put("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value());

        // Then - Verify the feature was updated with completion dates
        given().when()
                .get("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("code", equalTo(featureCode))
                .body("title", equalTo("Completed Feature"))
                .body("status", equalTo("RELEASED"))
                .body("plannedCompletionDate", equalTo("2024-11-30"))
                .body("actualCompletionDate", equalTo("2024-11-28"))
                .body("featurePlanningStatus", equalTo("DONE"))
                .body("featureOwner", equalTo("project.manager@example.com"))
                .body("blockageReason", nullValue());
    }

    @Test
    @WithMockOAuth2User
    void shouldUpdateFeatureWithBlockageReason() {
        // Given - using existing feature from test data
        String featureCode = "BOOKSTORE-103";
        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                "Blocked Feature",
                "Feature that is blocked",
                null,
                "developer@example.com",
                FeatureStatus.ON_HOLD,
                LocalDate.of(2025, 2, 15),
                null,
                FeaturePlanningStatus.BLOCKED,
                "team.lead@example.com",
                "Waiting for external API approval and security review");

        // When
        given().contentType(ContentType.JSON)
                .body(payload)
                .when()
                .put("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value());

        // Then - Verify the feature was updated with blockage information
        given().when()
                .get("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("code", equalTo(featureCode))
                .body("title", equalTo("Blocked Feature"))
                .body("status", equalTo("ON_HOLD"))
                .body("plannedCompletionDate", equalTo("2025-02-15"))
                .body("actualCompletionDate", nullValue())
                .body("featurePlanningStatus", equalTo("BLOCKED"))
                .body("featureOwner", equalTo("team.lead@example.com"))
                .body("blockageReason", equalTo("Waiting for external API approval and security review"));
    }

    @Test
    @WithMockOAuth2User
    void shouldGetFeaturesByProductWithPlanningFields() {
        // Given - First update a feature with planning fields
        String featureCode = "BOOKSTORE-104";
        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                "Product Feature with Planning",
                "Feature for product query test",
                null,
                "product.owner@example.com",
                FeatureStatus.IN_PROGRESS,
                LocalDate.of(2024, 12, 20),
                null,
                FeaturePlanningStatus.IN_PROGRESS,
                "product.manager@example.com",
                "In active development");

        given().contentType(ContentType.JSON)
                .body(payload)
                .when()
                .put("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.OK.value());

        // When - Get features by product
        given().queryParam("productCode", "bookstore")
                .when()
                .get("/api/features")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("$", hasSize(greaterThan(0)))
                .body("find { it.code == '" + featureCode + "' }.plannedCompletionDate", equalTo("2024-12-20"))
                .body("find { it.code == '" + featureCode + "' }.featurePlanningStatus", equalTo("IN_PROGRESS"))
                .body("find { it.code == '" + featureCode + "' }.featureOwner", equalTo("product.manager@example.com"))
                .body("find { it.code == '" + featureCode + "' }.blockageReason", equalTo("In active development"));
    }

    @Test
    @WithMockOAuth2User
    void shouldValidateFeatureOwnerLength() {
        // Given - Feature with too long owner name
        String featureCode = "BOOKSTORE-100";
        String longOwnerName = "a".repeat(256); // Exceeds 255 character limit

        UpdateFeaturePayload payload = new UpdateFeaturePayload(
                "Feature with long owner",
                "Test validation",
                null,
                "admin@example.com",
                FeatureStatus.NEW,
                LocalDate.of(2024, 12, 31),
                null,
                FeaturePlanningStatus.NOT_STARTED,
                longOwnerName,
                "Test blockage");

        // When & Then
        given().contentType(ContentType.JSON)
                .body(payload)
                .when()
                .put("/api/features/{code}", featureCode)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }
}
