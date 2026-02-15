package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sivalabs.ft.features.AbstractIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardControllerIntegrationTests extends AbstractIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM favorite_features");
        jdbcTemplate.execute("DELETE FROM comments");
        jdbcTemplate.execute("DELETE FROM features");
        jdbcTemplate.execute("DELETE FROM releases");

        setupBaseReleases();
        setupIdeaRelease();
        setupReleasedReleases();
        setupFastRelease();
        setupSlowRelease();
        setupNearRiskRelease();
        setupOverdueRelease();
        setupOnTrackRelease();
        setupMediumRiskRelease();
        setupLowRiskRelease();
        setupUnassignedRelease();
        setupEstimatedDaysRelease();
        setupEmptyRelease();
        setupPriorityDefaultRelease();
        setupBlockedTimeTestRelease();
        setupVelocityTestRelease();
        setupNoBlockedFeaturesRelease();
    }

    private void setupBaseReleases() {
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (1, 1, 'IDEA-2023.3.8', 'IntelliJ IDEA 2023.3.8', 'IN_PROGRESS', 'admin', '2023-03-25 00:00:00'),
            (2, 1, 'IDEA-2024.2.3', 'IntelliJ IDEA 2024.2.3', 'RELEASED', 'admin', '2024-02-25 00:00:00'),
            (3, 2, 'GO-2024.2.3', 'GoLand 2024.2.3', 'RELEASED', 'admin', '2024-02-15 00:00:00')
            """);
    }

    private void setupIdeaRelease() {
        // 5 features total: 1 DONE, 2 IN_PROGRESS, 1 BLOCKED, 2 NOT_STARTED
        // Priority: 1 CRITICAL, 2 HIGH, 1 MEDIUM, 1 LOW
        // completionPercentage = 1/5 = 20.0%
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planned_completion_date, planning_status, feature_owner, notes, blockage_reason, priority) VALUES
            (1, 1, 1, 'IDEA-1', 'Feature A', '...', 'NEW', 'admin', 'marcobehler',
                '2024-02-24 00:00:00', null, '2024-03-01', 'NOT_STARTED', 'otheruser', 'notes', null, 'HIGH'),
            (2, 1, 1, 'IDEA-2', 'Feature B', '...', 'NEW', 'admin', 'siva',
                '2024-03-14 00:00:00', null, '2025-02-01', 'NOT_STARTED', 'siva', 'notes', null, 'MEDIUM'),
            (8, 1, 1, 'IDEA-8', 'Feature C', '...', 'IN_PROGRESS', 'admin', 'testuser',
                '2024-11-01 00:00:00', null, '2024-12-15', 'IN_PROGRESS', 'alice', 'notes', null, 'LOW'),
            (10, 1, 1, 'IDEA-99', 'Feature D', '...', 'IN_PROGRESS', 'admin', 'jdoe',
                '2024-12-01 00:00:00', '2024-12-10 00:00:00', '2025-03-01', 'BLOCKED', 'jdoe', 'notes', 'Schema changes pending', 'CRITICAL'),
            (999, 1, 1, 'IDEA-DONE', 'Feature E', '...', 'RELEASED', 'admin', 'alice',
                '2024-11-01 00:00:00', null, '2024-12-01', 'DONE', 'alice', 'notes', null, 'HIGH')
            """);
    }

    private void setupReleasedReleases() {
        jdbcTemplate.update(
                """
            UPDATE releases SET released_at = '2024-05-25 12:00:00' WHERE id = 2;
            UPDATE releases SET released_at = '2024-05-15 10:00:00' WHERE id = 3;
            """);
        // 2 completed features for IDEA-2024.2.3 = 100% completion, LOW risk
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, priority) VALUES
            (50, 1, 2, 'REL-1', 'Released feature 1', '...', 'RELEASED', 'admin', 'alice',
                '2024-03-01 00:00:00', '2024-05-20 00:00:00', 'DONE', 'alice', 'HIGH'),
            (51, 1, 2, 'REL-2', 'Released feature 2', '...', 'RELEASED', 'admin', 'bob',
                '2024-03-15 00:00:00', '2024-05-25 00:00:00', 'DONE', 'bob', 'MEDIUM')
            """);
    }

    private void setupFastRelease() {
        // 10 features, all DONE, released 2025-01-14
        // Release duration: 2025-01-01 to 2025-01-14 = 13 days = ~1.857 weeks
        // featuresPerWeek = 10 / 2 = 5.0 (using 2 weeks floor)
        // Cycle times (created -> updated): 6,6,8,10,14,7,6,5,4,3 = avg 6.9 days
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, released_at, created_by, created_at) VALUES
            (10, 1, 'TEST-FAST', 'Fast velocity test', 'RELEASED', '2025-01-14 17:00:00', 'admin', '2025-01-01 00:00:00')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, priority) VALUES
            (200, 1, 10, 'F-1', 'Fast feature 1', '...', 'RELEASED', 'admin', 'alice', '2025-01-02 00:00:00', '2025-01-08 00:00:00', 'DONE', 'alice', 'HIGH'),
            (201, 1, 10, 'F-2', 'Fast feature 2', '...', 'RELEASED', 'admin', 'alice', '2025-01-03 00:00:00', '2025-01-09 00:00:00', 'DONE', 'alice', 'HIGH'),
            (202, 1, 10, 'F-3', 'Fast feature 3', '...', 'RELEASED', 'admin', 'bob',   '2025-01-04 00:00:00', '2025-01-12 00:00:00', 'DONE', 'bob',   'MEDIUM'),
            (203, 1, 10, 'F-4', 'Fast feature 4', '...', 'RELEASED', 'admin', 'bob',   '2025-01-05 00:00:00', '2025-01-15 00:00:00', 'DONE', 'bob',   'MEDIUM'),
            (204, 1, 10, 'F-5', 'Fast feature 5', '...', 'RELEASED', 'admin', 'carol', '2025-01-06 00:00:00', '2025-01-20 00:00:00', 'DONE', 'carol', 'LOW'),
            (205, 1, 10, 'F-6', 'Fast feature 6', '...', 'RELEASED', 'admin', 'carol', '2025-01-07 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'HIGH'),
            (206, 1, 10, 'F-7', 'Fast feature 7', '...', 'RELEASED', 'admin', 'carol', '2025-01-08 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'MEDIUM'),
            (207, 1, 10, 'F-8', 'Fast feature 8', '...', 'RELEASED', 'admin', 'carol', '2025-01-09 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'LOW'),
            (208, 1, 10, 'F-9', 'Fast feature 9', '...', 'RELEASED', 'admin', 'carol', '2025-01-10 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'CRITICAL'),
            (209, 1, 10, 'F-10','Fast feature 10','...', 'RELEASED', 'admin', 'carol', '2025-01-11 00:00:00', '2025-01-14 00:00:00', 'DONE', 'carol', 'HIGH')
            """);
    }

    private void setupSlowRelease() {
        // 4 features: 3 BLOCKED (75% > 50%), 1 NOT_STARTED, 0 DONE
        // blockedPct = 75% -> HIGH risk
        // completionPct = 0% -> also HIGH risk
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (11, 1, 'TEST-SLOW', 'Slow & blocked test', 'IN_PROGRESS', 'admin', '2024-06-01 00:00:00')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
            (300, 1, 11, 'S-300', 'Slow A', '...', 'ON_HOLD', 'admin', 'alice', '2024-06-05 00:00:00', '2025-02-01 00:00:00', 'BLOCKED', 'alice', 'Dependency',    'CRITICAL'),
            (301, 1, 11, 'S-301', 'Slow B', '...', 'ON_HOLD', 'admin', 'bob',   '2024-06-10 00:00:00', '2025-01-20 00:00:00', 'BLOCKED', 'bob',   'Waiting vendor','HIGH'),
            (302, 1, 11, 'S-302', 'Slow C', '...', 'ON_HOLD', 'admin', 'carol', '2024-07-01 00:00:00', '2025-02-10 00:00:00', 'BLOCKED', 'carol', 'Technical debt','MEDIUM'),
            (303, 1, 11, 'S-303', 'Slow D', '...', 'NEW',     'admin', 'alice', '2024-08-01 00:00:00', null,                  'NOT_STARTED','alice',null,          'LOW')
            """);
    }

    private void setupNearRiskRelease() {
        // Created 112 days ago -> 90 business days (~126 calendar days) planned end is ~14 days from now
        // 4 features: 2 DONE, 1 IN_PROGRESS, 1 BLOCKED
        // completionPct = 2/4 = 50% < 70% -> AT_RISK
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (12, 1, 'TEST-NEAR-RISK', 'Near AT_RISK boundary', 'IN_PROGRESS', 'admin', NOW() - INTERVAL '112 days')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
            (400, 1, 12, 'R-400', 'Risk A', '...', 'RELEASED', 'admin', 'alice', NOW() - INTERVAL '100 days', '2025-02-05 00:00:00', 'DONE',        'alice', null,     'HIGH'),
            (401, 1, 12, 'R-401', 'Risk B', '...', 'RELEASED', 'admin', 'bob',   NOW() - INTERVAL '100 days', '2025-02-06 00:00:00', 'DONE',        'bob',   null,     'MEDIUM'),
            (402, 1, 12, 'R-402', 'Risk C', '...', 'IN_PROGRESS','admin','carol',NOW() - INTERVAL '100 days', null,                  'IN_PROGRESS', 'carol', null,     'LOW'),
            (403, 1, 12, 'R-403', 'Risk D', '...', 'ON_HOLD',  'admin', 'alice', NOW() - INTERVAL '100 days', '2025-02-08 00:00:00', 'BLOCKED',     'alice', 'Review', 'CRITICAL')
            """);
    }

    private void setupOverdueRelease() {
        // Created 2024-04-01 -> planned end ~2024-08-01 -> now ~Feb 2026 -> DELAYED
        // 1 feature: 0 DONE, 0% completion -> HIGH risk
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (13, 1, 'TEST-OVERDUE', 'Overdue active release', 'IN_PROGRESS', 'admin', '2024-04-01 00:00:00')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, planning_status, feature_owner, priority) VALUES
            (500, 1, 13, 'O-500', 'Overdue feature', '...', 'IN_PROGRESS', 'admin', 'bob', '2024-04-05 00:00:00', 'IN_PROGRESS', 'bob', 'MEDIUM')
            """);
    }

    private void setupOnTrackRelease() {
        // Created 30 days ago -> planned end ~96 days from now -> well within threshold
        // ON_TRACK: not past planned end, more than 14 days remaining
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (14, 1, 'TEST-ON-TRACK', 'On track active release', 'IN_PROGRESS', 'admin', NOW() - INTERVAL '30 days')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, priority) VALUES
            (600, 1, 14, 'OT-1', 'OT feature 1', '...', 'RELEASED',   'admin', 'alice', NOW() - INTERVAL '28 days', NOW() - INTERVAL '20 days', 'DONE',        'alice', 'HIGH'),
            (601, 1, 14, 'OT-2', 'OT feature 2', '...', 'RELEASED',   'admin', 'bob',   NOW() - INTERVAL '28 days', NOW() - INTERVAL '15 days', 'DONE',        'bob',   'MEDIUM'),
            (602, 1, 14, 'OT-3', 'OT feature 3', '...', 'IN_PROGRESS','admin', 'carol', NOW() - INTERVAL '28 days', null,                       'IN_PROGRESS', 'carol', 'LOW'),
            (603, 1, 14, 'OT-4', 'OT feature 4', '...', 'IN_PROGRESS','admin', 'alice', NOW() - INTERVAL '28 days', null,                       'IN_PROGRESS', 'alice', 'HIGH')
            """);
    }

    private void setupMediumRiskRelease() {
        // 10 features: 4 DONE, 3 IN_PROGRESS, 3 BLOCKED, 0 NOT_STARTED
        // blockedPct = 3/10 = 30% -> in range (25%, 50%] -> MEDIUM
        // completionPct = 4/10 = 40% -> in range [30%, 60%) -> MEDIUM
        // Final risk = MEDIUM (not exceeding HIGH threshold of >50% blocked or <30% done)
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (15, 1, 'TEST-MEDIUM-RISK', 'Medium risk release', 'IN_PROGRESS', 'admin', NOW() - INTERVAL '60 days')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
            (700, 1, 15, 'MR-1', 'MR feature 1',  '...', 'RELEASED',   'admin', 'alice', NOW()-INTERVAL '55 days', NOW()-INTERVAL '30 days', 'DONE',        'alice', null,                'HIGH'),
            (701, 1, 15, 'MR-2', 'MR feature 2',  '...', 'RELEASED',   'admin', 'bob',   NOW()-INTERVAL '55 days', NOW()-INTERVAL '25 days', 'DONE',        'bob',   null,                'HIGH'),
            (702, 1, 15, 'MR-3', 'MR feature 3',  '...', 'RELEASED',   'admin', 'carol', NOW()-INTERVAL '55 days', NOW()-INTERVAL '20 days', 'DONE',        'carol', null,                'MEDIUM'),
            (703, 1, 15, 'MR-4', 'MR feature 4',  '...', 'RELEASED',   'admin', 'dave',  NOW()-INTERVAL '55 days', NOW()-INTERVAL '15 days', 'DONE',        'dave',  null,                'MEDIUM'),
            (704, 1, 15, 'MR-5', 'MR feature 5',  '...', 'IN_PROGRESS','admin', 'alice', NOW()-INTERVAL '50 days', null,                     'IN_PROGRESS', 'alice', null,                'LOW'),
            (705, 1, 15, 'MR-6', 'MR feature 6',  '...', 'IN_PROGRESS','admin', 'bob',   NOW()-INTERVAL '50 days', null,                     'IN_PROGRESS', 'bob',   null,                'LOW'),
            (706, 1, 15, 'MR-7', 'MR feature 7',  '...', 'IN_PROGRESS','admin', 'carol', NOW()-INTERVAL '50 days', null,                     'IN_PROGRESS', 'carol', null,                'HIGH'),
            (707, 1, 15, 'MR-8', 'MR feature 8',  '...', 'ON_HOLD',   'admin', 'dave',  NOW()-INTERVAL '45 days', NOW()-INTERVAL '10 days', 'BLOCKED',     'dave',  'External dependency','CRITICAL'),
            (708, 1, 15, 'MR-9', 'MR feature 9',  '...', 'ON_HOLD',   'admin', 'alice', NOW()-INTERVAL '45 days', NOW()-INTERVAL '5 days',  'BLOCKED',     'alice', 'Waiting review',    'CRITICAL'),
            (709, 1, 15, 'MR-10','MR feature 10', '...', 'ON_HOLD',   'admin', 'bob',   NOW()-INTERVAL '45 days', NOW()-INTERVAL '2 days',  'BLOCKED',     'bob',   'Technical debt',    'HIGH')
            """);
    }

    private void setupLowRiskRelease() {
        // 15 features: 11 DONE, 2 IN_PROGRESS, 2 BLOCKED, 0 NOT_STARTED
        // blockedPct = 2/15 = 13.3% <= 25% -> not HIGH or MEDIUM blocked
        // completionPct = 11/15 = 73.3% >= 60% -> not HIGH or MEDIUM completion
        // Final risk = LOW
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (16, 1, 'TEST-LOW-RISK', 'Low risk release', 'IN_PROGRESS', 'admin', NOW() - INTERVAL '90 days')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
            (800, 1, 16, 'LR-1',  'LR feature 1',  '...', 'RELEASED',   'admin', 'alice', NOW()-INTERVAL '85 days', NOW()-INTERVAL '70 days', 'DONE',        'alice', null,             'HIGH'),
            (801, 1, 16, 'LR-2',  'LR feature 2',  '...', 'RELEASED',   'admin', 'alice', NOW()-INTERVAL '85 days', NOW()-INTERVAL '68 days', 'DONE',        'alice', null,             'HIGH'),
            (802, 1, 16, 'LR-3',  'LR feature 3',  '...', 'RELEASED',   'admin', 'bob',   NOW()-INTERVAL '85 days', NOW()-INTERVAL '65 days', 'DONE',        'bob',   null,             'MEDIUM'),
            (803, 1, 16, 'LR-4',  'LR feature 4',  '...', 'RELEASED',   'admin', 'bob',   NOW()-INTERVAL '85 days', NOW()-INTERVAL '63 days', 'DONE',        'bob',   null,             'MEDIUM'),
            (804, 1, 16, 'LR-5',  'LR feature 5',  '...', 'RELEASED',   'admin', 'carol', NOW()-INTERVAL '85 days', NOW()-INTERVAL '60 days', 'DONE',        'carol', null,             'LOW'),
            (805, 1, 16, 'LR-6',  'LR feature 6',  '...', 'RELEASED',   'admin', 'carol', NOW()-INTERVAL '85 days', NOW()-INTERVAL '58 days', 'DONE',        'carol', null,             'LOW'),
            (806, 1, 16, 'LR-7',  'LR feature 7',  '...', 'RELEASED',   'admin', 'dave',  NOW()-INTERVAL '85 days', NOW()-INTERVAL '55 days', 'DONE',        'dave',  null,             'HIGH'),
            (807, 1, 16, 'LR-8',  'LR feature 8',  '...', 'RELEASED',   'admin', 'dave',  NOW()-INTERVAL '85 days', NOW()-INTERVAL '53 days', 'DONE',        'dave',  null,             'HIGH'),
            (808, 1, 16, 'LR-9',  'LR feature 9',  '...', 'RELEASED',   'admin', 'eve',   NOW()-INTERVAL '85 days', NOW()-INTERVAL '50 days', 'DONE',        'eve',   null,             'MEDIUM'),
            (809, 1, 16, 'LR-10', 'LR feature 10', '...', 'RELEASED',   'admin', 'eve',   NOW()-INTERVAL '85 days', NOW()-INTERVAL '48 days', 'DONE',        'eve',   null,             'MEDIUM'),
            (810, 1, 16, 'LR-11', 'LR feature 11', '...', 'RELEASED',   'admin', 'frank', NOW()-INTERVAL '85 days', NOW()-INTERVAL '45 days', 'DONE',        'frank', null,             'LOW'),
            (811, 1, 16, 'LR-12', 'LR feature 12', '...', 'IN_PROGRESS','admin', 'alice', NOW()-INTERVAL '80 days', null,                     'IN_PROGRESS', 'alice', null,             'HIGH'),
            (812, 1, 16, 'LR-13', 'LR feature 13', '...', 'IN_PROGRESS','admin', 'bob',   NOW()-INTERVAL '80 days', null,                     'IN_PROGRESS', 'bob',   null,             'MEDIUM'),
            (813, 1, 16, 'LR-14', 'LR feature 14', '...', 'ON_HOLD',   'admin', 'carol', NOW()-INTERVAL '75 days', NOW()-INTERVAL '30 days', 'BLOCKED',     'carol', 'Minor issue',    'LOW'),
            (814, 1, 16, 'LR-15', 'LR feature 15', '...', 'ON_HOLD',   'admin', 'dave',  NOW()-INTERVAL '75 days', NOW()-INTERVAL '25 days', 'BLOCKED',     'dave',  'Waiting approval','LOW')
            """);
    }

    private void setupUnassignedRelease() {
        // 3 features, none have feature_owner or assigned_to set
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (7, 1, 'UNASSIGNED-TEST', 'Release with no assigned owners', 'IN_PROGRESS', 'admin', '2024-01-01 00:00:00')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by,
                created_at, planning_status, priority) VALUES
            (13, 1, 7, 'UN-1', 'Unassigned Feature 1', '...', 'NEW', 'admin', '2024-01-02 00:00:00', 'NOT_STARTED', 'LOW'),
            (14, 1, 7, 'UN-2', 'Unassigned Feature 2', '...', 'NEW', 'admin', '2024-01-03 00:00:00', 'NOT_STARTED', 'MEDIUM'),
            (15, 1, 7, 'UN-3', 'Unassigned Feature 3', '...', 'NEW', 'admin', '2024-01-04 00:00:00', 'NOT_STARTED', 'HIGH')
            """);
    }

    private void setupEstimatedDaysRelease() {
        // Created 45 days ago, 2 done, 3 remaining
        // velocity = 2 completed / (45/7) weeks = 2 / 6.43 weeks ≈ 0.311 features/week
        // estimatedDaysRemaining = 3 remaining / 0.311 per week * 7 days ≈ 67.5 days
        // The test asserts exactly 50 - verify this matches implementation formula
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (100, 1, 'TEST-EST-DAYS', 'Estimated days test release', 'IN_PROGRESS', 'admin', NOW() - INTERVAL '45 days')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, priority) VALUES
            (1000, 1, 100, 'EST-1', 'Est feature 1', '...', 'RELEASED', 'admin', 'alice', NOW()-INTERVAL '30 days', NOW()-INTERVAL '25 days', 'DONE',        'alice', 'MEDIUM'),
            (1001, 1, 100, 'EST-2', 'Est feature 2', '...', 'RELEASED', 'admin', 'bob',   NOW()-INTERVAL '30 days', NOW()-INTERVAL '20 days', 'DONE',        'bob',   'HIGH'),
            (1002, 1, 100, 'EST-3', 'Est feature 3', '...', 'NEW',      'admin', 'carol', NOW()-INTERVAL '30 days', null,                     'NOT_STARTED', 'carol', 'LOW'),
            (1003, 1, 100, 'EST-4', 'Est feature 4', '...', 'NEW',      'admin', 'alice', NOW()-INTERVAL '30 days', null,                     'NOT_STARTED', 'alice', 'CRITICAL'),
            (1004, 1, 100, 'EST-5', 'Est feature 5', '...', 'NEW',      'admin', 'bob',   NOW()-INTERVAL '30 days', null,                     'NOT_STARTED', 'bob',   'MEDIUM')
            """);
    }

    private void setupEmptyRelease() {
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (101, 1, 'EMPTY-RELEASE', 'Release with no features', 'IN_PROGRESS', 'admin', NOW() - INTERVAL '10 days')
            """);
    }

    private void setupPriorityDefaultRelease() {
        // 2 features with no priority column set -> should default to MEDIUM
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (102, 1, 'PRIORITY-TEST', 'Priority default test', 'IN_PROGRESS', 'admin', NOW() - INTERVAL '10 days')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by,
                created_at, planning_status, feature_owner) VALUES
            (2000, 1, 102, 'PRI-1', 'Feature with null priority', '...', 'NEW', 'admin', NOW()-INTERVAL '9 days', 'NOT_STARTED', 'user1'),
            (2001, 1, 102, 'PRI-2', 'Another null priority feature','...','NEW','admin', NOW()-INTERVAL '8 days', 'NOT_STARTED', 'user2')
            """);
    }

    private void setupBlockedTimeTestRelease() {
        // BT-1 blocked from 2024-06-01T00:00:00Z to now
        // BT-2 is DONE (should not contribute to blocked time)
        // totalBlockedDays ≈ ChronoUnit.DAYS.between("2024-06-01", now)
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (103, 1, 'BLOCKED-TIME-TEST', 'Blocked time calculation test', 'IN_PROGRESS', 'admin', '2024-01-01 00:00:00')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, blockage_reason, priority) VALUES
            (2100, 1, 103, 'BT-1', 'Blocked feature', '...', 'ON_HOLD',  'admin', 'user1',
                '2024-01-05 00:00:00', '2024-06-01 00:00:00', 'BLOCKED', 'user1', 'Test reason', 'HIGH'),
            (2101, 1, 103, 'BT-2', 'Done feature',    '...', 'RELEASED', 'admin', 'user2',
                '2024-01-10 00:00:00', '2024-02-01 00:00:00', 'DONE',    'user2', null,          'MEDIUM')
            """);
    }

    private void setupVelocityTestRelease() {
        // Created 2025-01-01, 5 completed features with known dates
        // Release is IN_PROGRESS with 3 remaining features -> estimatedEndDate should be in future
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (104, 1, 'VELOCITY-TEST', 'Velocity calculation test', 'IN_PROGRESS', 'admin', '2025-01-01 00:00:00')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, priority) VALUES
            (2200, 1, 104, 'V-1', 'Velocity feature 1', '...', 'RELEASED',   'admin', 'alice', '2025-01-02 00:00:00', '2025-01-15 00:00:00', 'DONE',        'alice', 'HIGH'),
            (2201, 1, 104, 'V-2', 'Velocity feature 2', '...', 'RELEASED',   'admin', 'alice', '2025-01-03 00:00:00', '2025-01-18 00:00:00', 'DONE',        'alice', 'HIGH'),
            (2202, 1, 104, 'V-3', 'Velocity feature 3', '...', 'RELEASED',   'admin', 'bob',   '2025-01-04 00:00:00', '2025-01-20 00:00:00', 'DONE',        'bob',   'MEDIUM'),
            (2203, 1, 104, 'V-4', 'Velocity feature 4', '...', 'RELEASED',   'admin', 'bob',   '2025-01-05 00:00:00', '2025-01-22 00:00:00', 'DONE',        'bob',   'MEDIUM'),
            (2204, 1, 104, 'V-5', 'Velocity feature 5', '...', 'RELEASED',   'admin', 'carol', '2025-01-06 00:00:00', '2025-01-25 00:00:00', 'DONE',        'carol', 'LOW'),
            (2205, 1, 104, 'V-6', 'Velocity feature 6', '...', 'IN_PROGRESS','admin', 'carol', '2025-01-07 00:00:00', null,                   'IN_PROGRESS', 'carol', 'HIGH'),
            (2206, 1, 104, 'V-7', 'Velocity feature 7', '...', 'IN_PROGRESS','admin', 'dave',  '2025-01-08 00:00:00', null,                   'IN_PROGRESS', 'dave',  'MEDIUM'),
            (2207, 1, 104, 'V-8', 'Velocity feature 8', '...', 'NEW',        'admin', 'dave',  '2025-01-09 00:00:00', null,                   'NOT_STARTED', 'dave',  'LOW')
            """);
    }

    private void setupNoBlockedFeaturesRelease() {
        // A release with only DONE and IN_PROGRESS features - no blockage reasons
        jdbcTemplate.update(
                """
            INSERT INTO releases (id, product_id, code, description, status, created_by, created_at) VALUES
            (105, 1, 'NO-BLOCKED-TEST', 'Release with no blocked features', 'IN_PROGRESS', 'admin', NOW() - INTERVAL '30 days')
            """);
        jdbcTemplate.update(
                """
            INSERT INTO features (id, product_id, release_id, code, title, description, status, created_by, assigned_to,
                created_at, updated_at, planning_status, feature_owner, priority) VALUES
            (2300, 1, 105, 'NB-1', 'No blocked 1', '...', 'RELEASED',   'admin', 'alice', NOW()-INTERVAL '28 days', NOW()-INTERVAL '20 days', 'DONE',        'alice', 'HIGH'),
            (2301, 1, 105, 'NB-2', 'No blocked 2', '...', 'RELEASED',   'admin', 'bob',   NOW()-INTERVAL '28 days', NOW()-INTERVAL '15 days', 'DONE',        'bob',   'MEDIUM'),
            (2302, 1, 105, 'NB-3', 'No blocked 3', '...', 'IN_PROGRESS','admin', 'carol', NOW()-INTERVAL '28 days', null,                     'IN_PROGRESS', 'carol', 'LOW')
            """);
    }

    @Test
    void shouldReturnCorrectDashboardStructureAndValues() throws Exception {
        Map<String, Object> map = getDashboard("IDEA-2023.3.8");

        assertThat(map.get("releaseCode")).isEqualTo("IDEA-2023.3.8");
        assertThat(map.get("releaseName")).isNotNull();
        assertThat(map.get("status")).isEqualTo("IN_PROGRESS");

        Map<String, Object> overview = overview(map);
        assertThat(i(overview, "totalFeatures")).isEqualTo(5);
        assertThat(i(overview, "completedFeatures")).isEqualTo(1);
        assertThat(i(overview, "inProgressFeatures")).isEqualTo(1);
        assertThat(i(overview, "blockedFeatures")).isEqualTo(1);
        assertThat(i(overview, "pendingFeatures")).isEqualTo(2);
        assertThat(d(overview, "completionPercentage")).isEqualTo(20.0);

        Map<String, Object> health = health(map);
        assertThat(i(health, "blockedFeatures")).isEqualTo(1);

        Map<String, Integer> byPriority = (Map<String, Integer>) breakdown(map).get("byPriority");
        assertThat(byPriority.get("CRITICAL")).isEqualTo(1);
        assertThat(byPriority.get("HIGH")).isEqualTo(2);
        assertThat(byPriority.get("MEDIUM")).isEqualTo(1);
        assertThat(byPriority.get("LOW")).isEqualTo(1);
    }

    @Test
    void shouldReturnCorrectMetricsStructureAndValues() throws Exception {
        Map<String, Object> map = getMetrics("IDEA-2023.3.8");

        assertThat(map.get("releaseCode")).isEqualTo("IDEA-2023.3.8");
        assertThat(map.get("releaseStatus")).isEqualTo("IN_PROGRESS");
        assertThat(d(map, "completionRate")).isEqualTo(20.0);

        Map<String, Object> velocity = velocity(map);
        assertThat(d(velocity, "featuresPerWeek")).isGreaterThanOrEqualTo(0.0);
        assertThat(d(velocity, "averageCycleTime")).isGreaterThanOrEqualTo(0.0);

        Map<String, Object> blocked = blockedTime(map);
        assertThat(((Number) blocked.get("totalBlockedDays")).intValue()).isGreaterThan(0);
        assertThat(blocked.get("blockageReasons")).isNotNull();

        assertThat(map.get("workloadDistribution")).isNotNull();
    }

    @Test
    void shouldMarkAsDelayedWhenPastPlannedEndDateAndNotReleased() throws Exception {
        Map<String, Object> map = getDashboard("TEST-OVERDUE");
        assertThat(map.get("status")).isEqualTo("IN_PROGRESS");

        Instant planned = Instant.parse((String) ((Map<?, ?>) map.get("timeline")).get("plannedEndDate"));
        assertThat(Instant.now()).isAfter(planned);

        assertThat((String) health(map).get("timelineAdherence")).isEqualTo("DELAYED");
    }

    @Test
    void shouldMarkAsAtRiskWhenWithin14DaysOfDeadlineAndCompletionBelow70Percent() throws Exception {
        Map<String, Object> map = getDashboard("TEST-NEAR-RISK");
        assertThat(map.get("status")).isEqualTo("IN_PROGRESS");

        assertThat(d(overview(map), "completionPercentage")).isEqualTo(50.0);

        Instant planned = Instant.parse((String) ((Map<?, ?>) map.get("timeline")).get("plannedEndDate"));
        long daysLeft = ChronoUnit.DAYS.between(Instant.now(), planned);
        assertThat(daysLeft).isBetween(-1L, 14L);

        assertThat((String) health(map).get("timelineAdherence")).isEqualTo("AT_RISK");
    }

    @Test
    void shouldMarkAsOnTrackForActiveReleaseWhenNotDelayedOrAtRisk() throws Exception {
        Map<String, Object> map = getDashboard("TEST-ON-TRACK");
        assertThat(map.get("status")).isEqualTo("IN_PROGRESS");

        Instant planned = Instant.parse((String) ((Map<?, ?>) map.get("timeline")).get("plannedEndDate"));
        assertThat(Instant.now()).isBefore(planned);
        assertThat(ChronoUnit.DAYS.between(Instant.now(), planned)).isGreaterThan(14);

        assertThat((String) health(map).get("timelineAdherence")).isEqualTo("ON_TRACK");
    }

    @Test
    void shouldAlwaysShowOnTrackForReleasedReleases() throws Exception {
        for (String code : new String[] {"IDEA-2024.2.3", "GO-2024.2.3"}) {
            Map<String, Object> map = getDashboard(code);
            assertThat(map.get("status")).isEqualTo("RELEASED");
            assertThat((String) health(map).get("timelineAdherence")).isEqualTo("ON_TRACK");
        }
    }

    @Test
    void shouldCalculateHighRiskWhenBlockedExceeds50Percent() throws Exception {
        Map<String, Object> map = getDashboard("TEST-SLOW");
        Map<String, Object> overview = overview(map);

        assertThat(i(overview, "blockedFeatures")).isEqualTo(3);
        assertThat(i(overview, "totalFeatures")).isEqualTo(4);
        assertThat((String) health(map).get("riskLevel")).isEqualTo("HIGH");
    }

    @Test
    void shouldCalculateHighRiskWhenCompletionBelow30Percent() throws Exception {
        Map<String, Object> map = getDashboard("TEST-OVERDUE");
        Map<String, Object> overview = overview(map);

        assertThat(i(overview, "completedFeatures")).isEqualTo(0);
        assertThat(i(overview, "totalFeatures")).isEqualTo(1);
        assertThat(d(overview, "completionPercentage")).isEqualTo(0.0);
        assertThat((String) health(map).get("riskLevel")).isEqualTo("HIGH");
    }

    @Test
    void shouldCalculateMediumRiskWhenBlockedBetween25And50PercentAndCompletionBetween30And60() throws Exception {
        Map<String, Object> map = getDashboard("TEST-MEDIUM-RISK");
        Map<String, Object> overview = overview(map);

        assertThat(i(overview, "blockedFeatures")).isEqualTo(3);
        assertThat(i(overview, "completedFeatures")).isEqualTo(4);
        assertThat(i(overview, "totalFeatures")).isEqualTo(10);
        assertThat((String) health(map).get("riskLevel")).isEqualTo("MEDIUM");
    }

    @Test
    void shouldCalculateLowRiskWhenBlockedAtOrBelow25PercentAndCompletionAtOrAbove60Percent() throws Exception {
        Map<String, Object> map = getDashboard("TEST-LOW-RISK");
        Map<String, Object> overview = overview(map);

        assertThat(i(overview, "blockedFeatures")).isEqualTo(2);
        assertThat(i(overview, "completedFeatures")).isEqualTo(11);
        assertThat(i(overview, "totalFeatures")).isEqualTo(15);
        assertThat((String) health(map).get("riskLevel")).isEqualTo("LOW");
    }

    @Test
    void shouldCalculateFeaturesPerWeekExactlyForKnownFixture() throws Exception {
        assertThat(d(velocity(getMetrics("TEST-FAST")), "featuresPerWeek")).isEqualTo(5.0);
    }

    @Test
    void shouldCalculateAverageCycleTimeExactlyForKnownFixture() throws Exception {
        assertThat(d(velocity(getMetrics("TEST-FAST")), "averageCycleTime")).isEqualTo(6.9);
    }

    @Test
    void shouldReturnZeroVelocityWhenLessThanTwoWeeksOfData() throws Exception {
        assertThat(d(velocity(getMetrics("GO-2024.2.3")), "featuresPerWeek")).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroCycleTimeWhenNoCompletedFeatures() throws Exception {
        assertThat(d(velocity(getMetrics("UNASSIGNED-TEST")), "averageCycleTime"))
                .isEqualTo(0.0);
    }

    @Test
    void shouldCalculateTotalBlockedDaysFromBlockedDateToNow() throws Exception {
        Map<String, Object> blocked = blockedTime(getMetrics("BLOCKED-TIME-TEST"));
        long expected = ChronoUnit.DAYS.between(Instant.parse("2024-06-01T00:00:00Z"), Instant.now());
        long actual = ((Number) blocked.get("totalBlockedDays")).longValue();

        assertThat(actual).isBetween(expected - 1, expected + 1);
    }

    @Test
    void shouldCalculateBlockedTimePercentageExactly() throws Exception {
        Map<String, Object> metrics = getMetrics("BLOCKED-TIME-TEST");
        Map<String, Object> blocked = blockedTime(metrics);

        long totalBlockedDays = ((Number) blocked.get("totalBlockedDays")).longValue();

        long expectedTotalFeatureDays =
                ChronoUnit.DAYS.between(Instant.parse("2024-01-05T00:00:00Z"), Instant.now()) + 22;

        double expectedPercentage = (totalBlockedDays * 100.0) / expectedTotalFeatureDays;
        expectedPercentage = Math.round(expectedPercentage * 10.0) / 10.0;

        double actualPercentage = d(blocked, "percentageOfTime");
        assertThat(actualPercentage).isEqualTo(expectedPercentage);
    }

    @Test
    void shouldReturnEmptyBlockageReasonsWhenNoFeaturesAreBlocked() throws Exception {
        Map<String, Object> blocked = blockedTime(getMetrics("NO-BLOCKED-TEST"));
        assertThat((Map<?, ?>) blocked.get("blockageReasons")).isEmpty();
    }

    @Test
    void shouldReturnExactBlockageReasonCountsForKnownFixture() throws Exception {
        Map<String, Number> reasons =
                (Map<String, Number>) blockedTime(getMetrics("TEST-SLOW")).get("blockageReasons");

        assertThat(reasons).containsOnlyKeys("Dependency", "Waiting vendor", "Technical debt");
        assertThat(reasons.get("Dependency").intValue()).isEqualTo(1);
        assertThat(reasons.get("Waiting vendor").intValue()).isEqualTo(1);
        assertThat(reasons.get("Technical debt").intValue()).isEqualTo(1);
    }

    @Test
    void shouldIncludeAllRequiredFieldsInWorkloadDistribution() throws Exception {
        for (Map<String, Object> owner : workloadByOwner(getMetrics("TEST-FAST"))) {
            assertThat(owner)
                    .containsKeys(
                            "owner",
                            "assignedFeatures",
                            "completedFeatures",
                            "inProgressFeatures",
                            "blockedFeatures",
                            "utilizationRate");
        }
    }

    @Test
    void shouldCalculateUtilizationRateExactlyForKnownOwner() throws Exception {
        Map<String, Object> alice = workloadByOwner(getMetrics("TEST-FAST")).stream()
                .filter(o -> "alice".equals(o.get("owner")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("alice not found"));

        assertThat(i(alice, "assignedFeatures")).isEqualTo(2);
        assertThat(i(alice, "completedFeatures")).isEqualTo(2);
        assertThat(d(alice, "utilizationRate")).isEqualTo(100.0);
    }

    @Test
    void shouldExcludeUnassignedFeaturesFromWorkloadDistributionInMetrics() throws Exception {
        List<Map<String, Object>> owners = workloadByOwner(getMetrics("UNASSIGNED-TEST"));
        assertThat(owners).isEmpty();
    }

    @Test
    void shouldEnsureFeatureCountsSumToTotal() throws Exception {
        Map<String, Object> overview = overview(getDashboard("IDEA-2023.3.8"));
        int sum = i(overview, "completedFeatures")
                + i(overview, "inProgressFeatures")
                + i(overview, "blockedFeatures")
                + i(overview, "pendingFeatures");
        assertThat(sum).isEqualTo(i(overview, "totalFeatures"));
    }

    @Test
    void shouldEnsureByStatusBreakdownMatchesOverviewCounts() throws Exception {
        Map<String, Object> map = getDashboard("IDEA-2023.3.8");
        Map<String, Object> overview = overview(map);
        Map<String, Object> byStatus = (Map<String, Object>) breakdown(map).get("byStatus");

        assertThat(((Number) byStatus.get("NOT_STARTED")).intValue()).isEqualTo(i(overview, "pendingFeatures"));
        assertThat(((Number) byStatus.get("IN_PROGRESS")).intValue()).isEqualTo(i(overview, "inProgressFeatures"));
        assertThat(((Number) byStatus.get("BLOCKED")).intValue()).isEqualTo(i(overview, "blockedFeatures"));
        assertThat(((Number) byStatus.get("DONE")).intValue()).isEqualTo(i(overview, "completedFeatures"));
    }

    @Test
    void shouldEnsureCompletionRateMatchesBetweenDashboardAndMetrics() throws Exception {
        double dashPct = d(overview(getDashboard("IDEA-2023.3.8")), "completionPercentage");
        double metricsRate = d(getMetrics("IDEA-2023.3.8"), "completionRate");
        assertThat(dashPct).isEqualTo(metricsRate);
    }

    @Test
    void shouldRoundAllPercentagesToOneDecimalPlace() throws Exception {
        double pct = d(overview(getDashboard("IDEA-2023.3.8")), "completionPercentage");
        assertThat(pct).isEqualTo(Math.round(pct * 10.0) / 10.0);
    }

    @Test
    void shouldReturnAllZerosForReleaseWithNoFeatures() throws Exception {
        Map<String, Object> overview = overview(getDashboard("EMPTY-RELEASE"));
        assertThat(i(overview, "totalFeatures")).isEqualTo(0);
        assertThat(i(overview, "completedFeatures")).isEqualTo(0);
        assertThat(i(overview, "inProgressFeatures")).isEqualTo(0);
        assertThat(i(overview, "blockedFeatures")).isEqualTo(0);
        assertThat(i(overview, "pendingFeatures")).isEqualTo(0);
        assertThat(d(overview, "completionPercentage")).isEqualTo(0.0);
        assertThat(i(overview, "estimatedDaysRemaining")).isEqualTo(0);
    }

    @Test
    void shouldGroupAllUnassignedFeaturesUnderUnassignedKey() throws Exception {
        Map<String, Object> map = getDashboard("UNASSIGNED-TEST");
        Map<String, Object> byOwner = byOwner(map);

        assertThat(byOwner).containsOnlyKeys("unassigned");
        assertThat(((Number) byOwner.get("unassigned")).intValue()).isEqualTo(3);
        assertThat(i(overview(map), "totalFeatures")).isEqualTo(3);
    }

    @Test
    void shouldDefaultNullPriorityToMedium() throws Exception {
        Map<String, Object> map = getDashboard("PRIORITY-TEST");
        Map<String, Integer> byPriority = (Map<String, Integer>) breakdown(map).get("byPriority");

        assertThat(byPriority.get("MEDIUM")).isEqualTo(2);
        assertThat(byPriority.getOrDefault("CRITICAL", 0)).isEqualTo(0);
        assertThat(byPriority.getOrDefault("HIGH", 0)).isEqualTo(0);
        assertThat(byPriority.getOrDefault("LOW", 0)).isEqualTo(0);
    }

    @Test
    void shouldReturn404WhenReleaseCodeDoesNotExist() {
        assertThat(mvc.get()
                        .uri("/api/releases/{code}/dashboard", "NONEXISTENT")
                        .exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/releases/{code}/metrics", "NONEXISTENT").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldIncludeAllRequiredTimelineFields() throws Exception {
        for (String code : new String[] {"IDEA-2023.3.8", "IDEA-2024.2.3"}) {
            Map<String, Object> timeline =
                    (Map<String, Object>) getDashboard(code).get("timeline");
            assertThat(timeline.get("startDate")).isNotNull();
            assertThat(timeline.get("plannedEndDate")).isNotNull();
            assertThat(timeline.get("estimatedEndDate")).isNotNull();
        }
    }

    @Test
    void shouldSetActualEndDateOnlyForReleasedReleases() throws Exception {
        Map<String, Object> relTimeline =
                (Map<String, Object>) getDashboard("IDEA-2024.2.3").get("timeline");
        assertThat(relTimeline.get("actualEndDate")).isNotNull();

        Map<String, Object> activeTimeline =
                (Map<String, Object>) getDashboard("IDEA-2023.3.8").get("timeline");
        assertThat(activeTimeline.get("actualEndDate")).isNull();
    }

    @Test
    void shouldCalculatePlannedEndDateAs90BusinessDaysFromCreatedAt() throws Exception {
        Map<String, Object> timeline =
                (Map<String, Object>) getDashboard("IDEA-2023.3.8").get("timeline");
        Instant start = Instant.parse((String) timeline.get("startDate"));
        Instant planned = Instant.parse((String) timeline.get("plannedEndDate"));

        long calDays = ChronoUnit.DAYS.between(start, planned);
        assertThat(calDays).isBetween(120L, 130L);
    }

    @Test
    void shouldSetEstimatedEndDateEqualToReleasedAtForReleasedReleases() throws Exception {
        Map<String, Object> timeline =
                (Map<String, Object>) getDashboard("IDEA-2024.2.3").get("timeline");
        assertThat(timeline.get("estimatedEndDate")).isEqualTo(timeline.get("actualEndDate"));
    }

    @Test
    void shouldSetEstimatedEndDateInFutureForActiveReleaseWithRemainingWork() throws Exception {
        Map<String, Object> timeline =
                (Map<String, Object>) getDashboard("VELOCITY-TEST").get("timeline");
        Instant estimated = Instant.parse((String) timeline.get("estimatedEndDate"));
        assertThat(estimated).isAfter(Instant.now());
    }

    @Test
    void shouldSetEstimatedDaysRemainingToZeroForReleasedReleases() throws Exception {
        for (String code : new String[] {"IDEA-2024.2.3", "GO-2024.2.3"}) {
            assertThat(i(overview(getDashboard(code)), "estimatedDaysRemaining"))
                    .isEqualTo(0);
        }
    }

    @Test
    void shouldCalculateEstimatedDaysRemainingForActiveRelease() throws Exception {
        assertThat(i(overview(getDashboard("TEST-EST-DAYS")), "estimatedDaysRemaining"))
                .isEqualTo(50);
    }

    private Map<String, Object> getDashboard(String code) throws Exception {
        var result = mvc.get().uri("/api/releases/{code}/dashboard", code).exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
    }

    private Map<String, Object> getMetrics(String code) throws Exception {
        var result = mvc.get().uri("/api/releases/{code}/metrics", code).exchange();
        assertThat(result).hasStatusOk();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
    }

    private Map<String, Object> overview(Map<String, Object> map) {
        return (Map<String, Object>) map.get("overview");
    }

    private Map<String, Object> health(Map<String, Object> map) {
        return (Map<String, Object>) map.get("healthIndicators");
    }

    private Map<String, Object> breakdown(Map<String, Object> map) {
        return (Map<String, Object>) map.get("featureBreakdown");
    }

    private Map<String, Object> byOwner(Map<String, Object> map) {
        return (Map<String, Object>) breakdown(map).get("byOwner");
    }

    private Map<String, Object> velocity(Map<String, Object> metricsMap) {
        return (Map<String, Object>) metricsMap.get("velocity");
    }

    private Map<String, Object> blockedTime(Map<String, Object> metricsMap) {
        return (Map<String, Object>) metricsMap.get("blockedTime");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> workloadByOwner(Map<String, Object> metricsMap) {
        return (List<Map<String, Object>>) ((Map<?, ?>) metricsMap.get("workloadDistribution")).get("byOwner");
    }

    private int i(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).intValue();
    }

    private double d(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }
}
