package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sivalabs.ft.features.domain.Commands.CreateCommentCommand;
import com.sivalabs.ft.features.domain.entities.Comment;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.CommentMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceCreateCommentTest {
    @Mock
    CommentRepository commentRepository;

    @Mock
    FeatureRepository featureRepository;

    @Mock
    CommentMapper commentMapper;

    @InjectMocks
    CommentService service;

    @Test
    void shouldCreateCommentForExistingFeature() {
        var feature = mock(Feature.class);
        when(featureRepository.findByCode("IDEA-1")).thenReturn(Optional.of(feature));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            var comment = invocation.getArgument(0, Comment.class);
            comment.setId(99L);
            return comment;
        });

        var cmd = new CreateCommentCommand("IDEA-1", "Great feature!", "user1");
        Long commentId = service.createComment(cmd);

        assertThat(commentId).isEqualTo(99L);
        var captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Great feature!");
        assertThat(captor.getValue().getFeature()).isEqualTo(feature);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("user1");
    }

    @Test
    void shouldThrowWhenFeatureNotFound() {
        when(featureRepository.findByCode("INVALID")).thenReturn(Optional.empty());
        var cmd = new CreateCommentCommand("INVALID", "text", "user1");

        assertThatThrownBy(() -> service.createComment(cmd)).isInstanceOf(ResourceNotFoundException.class);
        verify(commentRepository, never()).save(any());
    }
}
