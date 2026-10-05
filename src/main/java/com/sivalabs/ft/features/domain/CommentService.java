package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.CommentDto;
import com.sivalabs.ft.features.domain.entities.Comment;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.CommentMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final FeatureRepository featureRepository;
    private final CommentMapper commentMapper;

    CommentService(
            CommentRepository commentRepository, FeatureRepository featureRepository, CommentMapper commentMapper) {
        this.commentRepository = commentRepository;
        this.featureRepository = featureRepository;
        this.commentMapper = commentMapper;
    }

    @Transactional
    public Long createComment(Commands.CreateCommentCommand command) {
        var feature = featureRepository
                .findByCode(command.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature with code %s not found.".formatted(command.featureCode())));

        Comment parentComment = null;
        int depth = 0;
        if (command.parentId() != null) {
            parentComment = commentRepository
                    .findById(command.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Comment with id %d not found.".formatted(command.parentId())));
            if (!parentComment.getFeature().getCode().equals(command.featureCode())) {
                throw new BadRequestException("Parent comment does not belong to the given feature");
            }
            depth = parentComment.getDepth() + 1;
            if (depth > Comment.MAX_DEPTH) {
                throw new BadRequestException("Cannot reply to this comment, maximum thread depth of %d reached"
                        .formatted(Comment.MAX_DEPTH));
            }
        }

        Comment comment = new Comment();
        comment.setContent(command.content());
        comment.setFeature(feature);
        comment.setParentComment(parentComment);
        comment.setDepth(depth);
        comment.setCreatedBy(command.createdBy());
        comment.setCreatedAt(Instant.now());
        commentRepository.save(comment);
        return comment.getId();
    }

    @Transactional
    public void removeComment(Long commentId, String userId) {
        if (commentRepository.countByParentCommentId(commentId) > 0) {
            throw new BadRequestException("Cannot remove a comment that has replies");
        }
        int count = commentRepository.deleteComment(commentId, userId);
        if (count != 1) {
            throw new BadRequestException("comment not found");
        }
    }

    @Transactional(readOnly = true)
    public List<CommentDto> findCommentsByFeatureCode(String featureCode, int page, int size) {
        List<Comment> comments = commentRepository.findCommentsByFeatureCode(featureCode);

        Map<Long, List<Comment>> childrenByParentId = comments.stream()
                .filter(c -> c.getParentComment() != null)
                .collect(Collectors.groupingBy(c -> c.getParentComment().getId()));

        List<Comment> rootComments =
                comments.stream().filter(c -> c.getParentComment() == null).toList();

        int fromIndex = Math.min(page * size, rootComments.size());
        int toIndex = Math.min(fromIndex + size, rootComments.size());

        return rootComments.subList(fromIndex, toIndex).stream()
                .map(comment -> buildCommentTree(comment, childrenByParentId))
                .toList();
    }

    private CommentDto buildCommentTree(Comment comment, Map<Long, List<Comment>> childrenByParentId) {
        List<CommentDto> replies = childrenByParentId.getOrDefault(comment.getId(), List.of()).stream()
                .map(reply -> buildCommentTree(reply, childrenByParentId))
                .toList();
        int nestedCommentsCount = replies.stream()
                .mapToInt(reply -> 1 + reply.nestedCommentsCount())
                .sum();
        CommentDto dto = commentMapper.toDto(comment);
        return new CommentDto(
                dto.id(),
                dto.featureCode(),
                dto.content(),
                dto.createdBy(),
                dto.createdAt(),
                dto.parentId(),
                dto.depth(),
                replies,
                nestedCommentsCount);
    }
}
