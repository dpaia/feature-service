package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.entities.Comment;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Product;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReleaseRepository releaseRepository;

    private Feature feature;
    private Release release;

    @BeforeEach
    void setUp() {
        Product product = new Product();
        product.setCode("comment-product");
        product.setPrefix("CMT");
        product.setName("Comment Product");
        product.setImageUrl("https://example.com/comment-product.png");
        product.setCreatedBy("tester");
        product.setCreatedAt(Instant.now());
        product = productRepository.save(product);

        release = new Release();
        release.setProduct(product);
        release.setCode("CMT-2026.1");
        release.setDescription("Comment release");
        release.setStatus(ReleaseStatus.DRAFT);
        release.setCreatedBy("tester");
        release.setCreatedAt(Instant.now());
        release = releaseRepository.save(release);

        feature = new Feature();
        feature.setProduct(product);
        feature.setRelease(release);
        feature.setCode("CMT-1");
        feature.setTitle("Comment feature");
        feature.setStatus(FeatureStatus.NEW);
        feature.setCreatedBy("tester");
        feature.setCreatedAt(Instant.now());
        feature = featureRepository.save(feature);
    }

    @Test
    void shouldFindFeatureCommentsIncludingReplies() {
        Comment topLevel = createFeatureComment("Top-level feature discussion", "alice");
        Comment reply = createFeatureComment("Reply in the same discussion", "bob");
        topLevel.addReply(reply);

        commentRepository.save(topLevel);

        assertThat(commentRepository.findByFeature(feature))
                .extracting(Comment::getText)
                .containsExactlyInAnyOrder("Top-level feature discussion", "Reply in the same discussion");
        assertThat(reply.getParentComment()).isEqualTo(topLevel);
        assertThat(topLevel.getReplies()).containsExactly(reply);
    }

    @Test
    void shouldFindReleaseCommentsAndCommentsByAuthor() {
        Comment releaseComment = new Comment();
        releaseComment.setRelease(release);
        releaseComment.setText("Release-level discussion");
        releaseComment.setAuthor("carol");
        releaseComment.setCreatedAt(Instant.now());

        Comment otherAuthorComment = createFeatureComment("Feature-level discussion", "dave");

        commentRepository.save(releaseComment);
        commentRepository.save(otherAuthorComment);

        assertThat(commentRepository.findByRelease(release))
                .extracting(Comment::getText)
                .containsExactly("Release-level discussion");
        assertThat(commentRepository.findByAuthor("carol"))
                .extracting(Comment::getRelease, Comment::getFeature)
                .containsExactly(tuple(release, null));
    }

    @Test
    void shouldRemoveReplyWithoutDeletingReply() {
        Comment topLevel = createFeatureComment("Parent", "alice");
        Comment reply = createFeatureComment("Reply", "bob");
        topLevel.addReply(reply);

        commentRepository.save(topLevel);
        Long replyId = reply.getId();

        topLevel.removeReply(reply);
        commentRepository.save(reply);

        assertThat(topLevel.getReplies()).isEmpty();
        assertThat(commentRepository.findById(replyId))
                .get()
                .extracting(Comment::getParentComment)
                .isNull();
    }

    private Comment createFeatureComment(String text, String author) {
        Comment comment = new Comment();
        comment.setFeature(feature);
        comment.setText(text);
        comment.setAuthor(author);
        comment.setCreatedAt(Instant.now());
        return comment;
    }
}
