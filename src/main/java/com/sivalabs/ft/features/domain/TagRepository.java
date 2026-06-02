package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Tag;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface TagRepository extends ListCrudRepository<Tag, Long> {
    List<Tag> findByNameContainingIgnoreCase(String name);
}
