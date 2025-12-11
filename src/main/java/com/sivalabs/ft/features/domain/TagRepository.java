package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface TagRepository extends ListCrudRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    boolean existsByName(String name);

    @Modifying
    @Query(nativeQuery = true, value = "delete from feature_tags where tag_id = :id")
    void unlinkTagFromFeatures(Long id);

    List<Tag> findByNameContainingIgnoreCase(String name);
}
