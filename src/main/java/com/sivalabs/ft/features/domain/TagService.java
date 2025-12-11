package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.CreateTagCommand;
import com.sivalabs.ft.features.domain.Commands.DeleteTagCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateTagCommand;
import com.sivalabs.ft.features.domain.dtos.TagDto;
import com.sivalabs.ft.features.domain.entities.Tag;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.TagMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    public Optional<TagDto> getTagById(Long id) {
        return tagRepository.findById(id).map(tagMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<TagDto> getTagByName(String name) {
        return tagRepository.findByName(name).map(tagMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isTagExists(Long id) {
        return tagRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public boolean isTagExistsByName(String name) {
        return tagRepository.existsByName(name);
    }

    @Transactional(readOnly = true)
    public List<TagDto> searchTags(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getAllTags();
        }
        List<Tag> tags = tagRepository.findByNameContainingIgnoreCase(name.trim());
        return tags.stream().map(tagMapper::toDto).toList();
    }

    @Transactional
    public Long createTag(CreateTagCommand cmd) {
        Tag tag = new Tag();
        tag.setName(cmd.name());
        tag.setDescription(cmd.description());
        tag.setCreatedBy(cmd.createdBy());
        tag.setCreatedAt(Instant.now());
        tag = tagRepository.save(tag);
        return tag.getId();
    }

    @Transactional
    public void updateTag(UpdateTagCommand cmd) {
        Tag tag = tagRepository
                .findById(cmd.id())
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found with id: " + cmd.id()));
        tag.setName(cmd.name());
        tag.setDescription(cmd.description());
        tagRepository.save(tag);
    }

    @Transactional
    public void deleteTag(DeleteTagCommand cmd) {
        if (!tagRepository.existsById(cmd.id())) {
            throw new ResourceNotFoundException("Tag not found with id: " + cmd.id());
        }
        tagRepository.unlinkTagFromFeatures(cmd.id());
        tagRepository.deleteById(cmd.id());
    }
}
