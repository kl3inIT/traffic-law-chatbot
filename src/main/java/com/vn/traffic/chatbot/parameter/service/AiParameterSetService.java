package com.vn.traffic.chatbot.parameter.service;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.repo.AiParameterSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AiParameterSetService {

    private final AiParameterSetRepository repository;

    @Transactional(readOnly = true)
    public List<AiParameterSet> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public AiParameterSet findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PARAMETER_SET_NOT_FOUND,
                        "AI parameter set not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<AiParameterSet> getActive() {
        return repository.findByActiveTrue();
    }

    public AiParameterSet create(String name, String content) {
        AiParameterSet entity = AiParameterSet.builder()
                .name(name)
                .content(content)
                .active(false)
                .build();
        return repository.save(entity);
    }

    public AiParameterSet update(UUID id, String name, String content) {
        AiParameterSet entity = findById(id);
        entity.setName(name);
        entity.setContent(content);
        return repository.save(entity);
    }

    public void delete(UUID id) {
        AiParameterSet entity = findById(id);
        if (entity.isActive()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot delete active parameter set");
        }
        repository.delete(entity);
    }

    public AiParameterSet activate(UUID id) {
        AiParameterSet entity = findById(id);
        repository.deactivateAll();
        entity.setActive(true);
        return repository.save(entity);
    }

    public AiParameterSet copy(UUID id) {
        AiParameterSet original = findById(id);
        AiParameterSet copy = AiParameterSet.builder()
                .name(original.getName() + " (ban sao)")
                .content(original.getContent())
                .active(false)
                .build();
        return repository.save(copy);
    }
}
