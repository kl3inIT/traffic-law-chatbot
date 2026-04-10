package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.domain.ChatMessage;
import com.vn.traffic.chatbot.chat.domain.ChatThread;
import com.vn.traffic.chatbot.chat.domain.ThreadFact;
import com.vn.traffic.chatbot.chat.domain.ThreadFactStatus;
import com.vn.traffic.chatbot.chat.repo.ThreadFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FactMemoryService {

    private static final Pattern VEHICLE_PATTERN = Pattern.compile("(?i)(xe máy điện|xe đạp điện|xe gắn máy|xe máy|mô tô|ô tô|xe hơi|xe tải|xe đạp)");
    private static final Pattern VIOLATION_PATTERN = Pattern.compile("(?i)(vượt đèn đỏ|vượt đèn vàng|không mang đăng ký xe|không mang cà vẹt|không có giấy phép lái xe|không có bằng lái|không đội mũ bảo hiểm|đi ngược chiều)");
    private static final Pattern ALCOHOL_PATTERN = Pattern.compile("(?i)(?:nồng độ cồn|cồn|rượu bia)[^.!?\\n]{0,60}?(không|có|dương tính|âm tính|vượt mức)");
    private static final Pattern LICENSE_PATTERN = Pattern.compile("(?i)(không có bằng lái|không có giấy phép lái xe|có bằng lái|có giấy phép lái xe)");
    private static final Pattern INJURY_PATTERN = Pattern.compile("(?i)(không ai bị thương|có người bị thương|gây tai nạn|va chạm)");
    private static final Pattern DOCUMENT_PATTERN = Pattern.compile("(?i)(không mang đăng ký xe|không mang cà vẹt|có đăng ký xe|mang đủ giấy tờ)");

    private final ThreadFactRepository threadFactRepository;

    @Transactional
    public List<ThreadFact> rememberExplicitFacts(ChatThread thread, ChatMessage userMessage) {
        List<ExtractedFact> extractedFacts = extractExplicitFacts(userMessage.getContent());
        List<ThreadFact> persistedFacts = new ArrayList<>();
        for (ExtractedFact extractedFact : extractedFacts) {
            persistedFacts.add(upsertFact(thread, userMessage, extractedFact));
        }
        return persistedFacts;
    }

    @Transactional(readOnly = true)
    public List<ThreadFact> getActiveFacts(ChatThread thread) {
        return threadFactRepository.findByThreadIdAndStatusOrderByCreatedAtAsc(thread.getId(), ThreadFactStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getActiveFactMap(ChatThread thread) {
        Map<String, String> factMap = new LinkedHashMap<>();
        for (ThreadFact fact : getActiveFacts(thread)) {
            factMap.put(fact.getFactKey(), fact.getFactValue());
        }
        return factMap;
    }

    public String buildThreadAwareQuestion(String question, List<ThreadFact> activeFacts) {
        if (activeFacts == null || activeFacts.isEmpty()) {
            return question;
        }
        String rememberedFacts = activeFacts.stream()
                .map(fact -> fact.getFactKey() + ": " + fact.getFactValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        return question + "\n\nSự kiện người dùng đã xác nhận trong cùng vụ việc: " + rememberedFacts;
    }

    List<ExtractedFact> extractExplicitFacts(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalized = normalize(content);
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();
        addMatch(facts, "vehicleType", VEHICLE_PATTERN.matcher(normalized));
        addMatch(facts, "violationType", VIOLATION_PATTERN.matcher(normalized));
        addMatch(facts, "alcoholStatus", ALCOHOL_PATTERN.matcher(normalized));
        addMatch(facts, "licenseStatus", LICENSE_PATTERN.matcher(normalized));
        addMatch(facts, "injuryStatus", INJURY_PATTERN.matcher(normalized));
        addMatch(facts, "documentStatus", DOCUMENT_PATTERN.matcher(normalized));
        return List.copyOf(facts.values());
    }

    private void addMatch(Map<String, ExtractedFact> facts, String factKey, Matcher matcher) {
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group();
            facts.put(factKey, new ExtractedFact(factKey, cleanupValue(value)));
        }
    }

    private ThreadFact upsertFact(ChatThread thread, ChatMessage userMessage, ExtractedFact extractedFact) {
        Optional<ThreadFact> existingActive = threadFactRepository
                .findFirstByThreadIdAndFactKeyAndStatusOrderByCreatedAtDesc(
                        thread.getId(),
                        extractedFact.factKey(),
                        ThreadFactStatus.ACTIVE
                );

        if (existingActive.isPresent() && existingActive.get().getFactValue().equalsIgnoreCase(extractedFact.factValue())) {
            return existingActive.get();
        }

        ThreadFact newFact = threadFactRepository.save(ThreadFact.builder()
                .thread(thread)
                .sourceMessage(userMessage)
                .factKey(extractedFact.factKey())
                .factValue(extractedFact.factValue())
                .status(ThreadFactStatus.ACTIVE)
                .build());

        existingActive.ifPresent(previous -> {
            previous.setStatus(ThreadFactStatus.SUPERSEDED);
            previous.setSupersededByFact(newFact);
            threadFactRepository.save(previous);
        });

        return newFact;
    }

    private String normalize(String content) {
        String lowerCase = Normalizer.normalize(content, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return lowerCase.replaceAll("\\s+", " ").trim();
    }

    private String cleanupValue(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    record ExtractedFact(String factKey, String factValue) {
    }
}
