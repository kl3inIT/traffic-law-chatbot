package com.vn.traffic.chatbot.checks.service;

import com.vn.traffic.chatbot.checks.api.dto.CheckDefRequest;
import com.vn.traffic.chatbot.checks.domain.CheckDef;
import com.vn.traffic.chatbot.checks.repo.CheckDefRepository;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckDefService {

    private final CheckDefRepository checkDefRepository;

    public CheckDef create(CheckDefRequest request) {
        CheckDef def = CheckDef.builder()
                .question(request.question())
                .referenceAnswer(request.referenceAnswer())
                .category(request.category())
                .active(request.active() != null ? request.active() : true)
                .build();
        return checkDefRepository.save(def);
    }

    public CheckDef update(UUID id, CheckDefRequest request) {
        CheckDef def = checkDefRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "CheckDef not found: " + id));
        def.setQuestion(request.question());
        def.setReferenceAnswer(request.referenceAnswer());
        def.setCategory(request.category());
        if (request.active() != null) {
            def.setActive(request.active());
        }
        return checkDefRepository.save(def);
    }

    public void delete(UUID id) {
        if (!checkDefRepository.existsById(id)) {
            throw new AppException(ErrorCode.NOT_FOUND, "CheckDef not found: " + id);
        }
        checkDefRepository.deleteById(id);
    }

    public List<CheckDef> findAll() {
        return checkDefRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<CheckDef> findActive() {
        return checkDefRepository.findByActiveTrue();
    }

    public Optional<CheckDef> findById(UUID id) {
        return checkDefRepository.findById(id);
    }
}
