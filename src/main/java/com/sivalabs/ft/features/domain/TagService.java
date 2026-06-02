package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.TagDto;
import com.sivalabs.ft.features.domain.entities.Tag;
import com.sivalabs.ft.features.domain.mappers.TagMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final TagRepository tagRepository;
    private final TagMapper tagMapper;

    public TagService(TagRepository tagRepository, TagMapper tagMapper) {
        this.tagRepository = tagRepository;
        this.tagMapper = tagMapper;
    }

    @Transactional(readOnly = true)
    public List<TagDto> getAllTags() {
        List<Tag> tags = tagRepository.findAll();
        return tags.stream().map(tagMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<TagDto> searchTags(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getAllTags();
        }
        List<Tag> tags = tagRepository.findByNameContainingIgnoreCase(name.trim());
        return tags.stream().map(tagMapper::toDto).toList();
    }
}
