package com.sivalabs.ft.features.review;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Gold check for the 'review' benchmark datapoint
 * <code>review-developer-access-control</code>.
 *
 * <p>The agent under evaluation reviews the uncommitted Developer-feature change,
 * which contains a seeded broken-access-control defect in {@code SecurityConfig}
 * (all {@code /api/developers/**} endpoints are made {@code permitAll()}, so the
 * mutating endpoints are publicly callable instead of admin-only). The
 * idea-review skill persists its findings to
 * {@code .idea/.review/sessions/rev-*.json}.
 *
 * <p>This test passes iff some persisted finding flags that defect: it must
 * anchor to {@code SecurityConfig.java}, mention an access-control keyword, and
 * carry warning/critical severity. It inspects only the session JSON and does
 * not start the application.
 */
class ReviewSessionAssertionTest {

    private static final Path SESSIONS_DIR = Path.of(".idea", ".review", "sessions");
    private static final String EXPECTED_FILE_SUFFIX = "config/SecurityConfig.java";
    private static final List<String> KEYWORDS_ANY =
            List.of("permitall", "access control", "authoriz", "admin", "unauthenticated", "publicly", "anyone");
    private static final List<String> SEVERITIES = List.of("warning", "critical");

    @Test
    void reviewSessionFlagsSecurityConfigAccessControl() throws IOException {
        List<JsonNode> sessions = loadSessions();
        assertFalse(
                sessions.isEmpty(),
                "No review session found under " + SESSIONS_DIR
                        + " (rev-*.json). The idea-review skill must persist a session.");

        List<String> seen = new ArrayList<>();
        for (JsonNode session : sessions) {
            for (JsonNode finding : session.path("findings")) {
                String file = finding.path("anchor").path("file").asText("");
                String text = finding.path("text").asText("").toLowerCase(Locale.ROOT);
                String severity = finding.path("severity").asText("").toLowerCase(Locale.ROOT);
                seen.add(severity + " @ " + file);

                boolean fileMatches = file.replace('\\', '/').endsWith(EXPECTED_FILE_SUFFIX);
                boolean keywordMatches = KEYWORDS_ANY.stream().anyMatch(text::contains);
                boolean severityMatches = SEVERITIES.contains(severity);
                if (fileMatches && keywordMatches && severityMatches) {
                    return;
                }
            }
        }

        throw new AssertionError("No review finding flagged the broken access control in " + EXPECTED_FILE_SUFFIX
                + ". Expected a warning/critical finding anchored there mentioning one of " + KEYWORDS_ANY
                + ". Findings seen: " + seen);
    }

    private static List<JsonNode> loadSessions() throws IOException {
        List<JsonNode> sessions = new ArrayList<>();
        if (!Files.isDirectory(SESSIONS_DIR)) {
            return sessions;
        }
        ObjectMapper mapper = new ObjectMapper();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SESSIONS_DIR, "rev-*.json")) {
            for (Path p : stream) {
                sessions.add(mapper.readTree(Files.readString(p)));
            }
        }
        return sessions;
    }
}
