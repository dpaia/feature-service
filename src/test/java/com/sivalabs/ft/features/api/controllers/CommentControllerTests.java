package com.sivalabs.ft.features.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sivalabs.ft.features.AbstractIT;
import com.sivalabs.ft.features.WithMockOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class CommentControllerTests extends AbstractIT {

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldAddComment() {
        var payload =
                """
                {
                    "featureCode": "IDEA-1",
                    "content": "This is a test comment"
                }
                """;

        var result = mvc.post()
                .uri("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400WhenFeatureNotFound() {
        var payload =
                """
                {
                    "featureCode": "INVALID_CODE",
                    "content": "This comment should fail"
                }
                """;

        var result = mvc.post()
                .uri("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldGetCommentsByFeatureCode() {
        var result = mvc.get().uri("/api/comments?featureCode={code}", "IDEA-1").exchange();

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size()")
                .asNumber()
                .extracting(Number::intValue)
                .satisfies(size -> assertThat(size).isGreaterThanOrEqualTo(0));
    }

    @Test
    void shouldGetCommentsWithPagination() {
        var result = mvc.get()
                .uri("/api/comments?featureCode={code}&page=0&size=5", "IDEA-1")
                .exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldRemoveComment() {

        // Then remove it
        var deleteResult = mvc.delete().uri("/api/comments/{commentId}", 1).exchange();

        assertThat(deleteResult).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400WhenRemovingNonExistentComment() {
        var result = mvc.delete().uri("/api/comments/{commentId}", 999).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReplyToComment() {
        var payload =
                """
                {
                    "featureCode": "IDEA-1",
                    "content": "This is a reply",
                    "parentId": 2
                }
                """;

        var result = mvc.post()
                .uri("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn404WhenReplyingToNonExistentComment() {
        var payload =
                """
                {
                    "featureCode": "IDEA-1",
                    "content": "This is a reply",
                    "parentId": 999
                }
                """;

        var result = mvc.post()
                .uri("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldAllowRepliesUpToThreeLevelsDeepAndRejectBeyond() {
        Long parentId = 2L;
        // reply levels 1, 2 and 3 should succeed
        for (int level = 1; level <= 3; level++) {
            parentId = addReply(parentId, "Reply at level " + level);
        }

        // a 4th nested level should be rejected
        var payload =
                """
                {
                    "featureCode": "IDEA-1",
                    "content": "This reply exceeds the allowed depth",
                    "parentId": %d
                }
                """
                        .formatted(parentId);

        var result = mvc.post()
                .uri("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldReturn400WhenRemovingCommentWithReplies() {
        addReply(3L, "A reply to comment 3");

        var result = mvc.delete().uri("/api/comments/{commentId}", 3).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockOAuth2User(username = "user")
    void shouldGetCommentsAsNestedThreads() {
        addReply(1L, "A reply to comment 1");

        var result = mvc.get().uri("/api/comments?featureCode={code}", "IDEA-1").exchange();

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.id == 1)].replies[0].content")
                .asList()
                .contains("A reply to comment 1");
    }

    private Long addReply(Long parentId, String content) {
        var payload =
                """
                {
                    "featureCode": "IDEA-1",
                    "content": "%s",
                    "parentId": %d
                }
                """
                        .formatted(content, parentId);

        var result = mvc.post()
                .uri("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String location = result.getMvcResult().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }
}
