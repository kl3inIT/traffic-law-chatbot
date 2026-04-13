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

    private static final Pattern VEHICLE_PATTERN = Pattern.compile(
            "(?i)(xe máy điện|xe đạp điện|xe gắn máy|xe máy|mô tô|ô tô|xe hơi|xe tải|xe buýt|xe khách|xe container|xe đạp)");
    private static final Pattern VIOLATION_PATTERN = Pattern.compile(
            "(?i)(vượt đèn đỏ|vượt đèn vàng|không mang đăng ký xe|không mang cà vẹt" +
            "|không có giấy phép lái xe|không có bằng lái|không đội mũ bảo hiểm|đi ngược chiều" +
            "|không có gương|thiếu gương|không gắn gương|không có đèn|thiếu đèn|không bật đèn" +
            "|dừng sai nơi|đỗ sai nơi|dừng xe|đỗ xe|không nhường đường|vượt phải" +
            "|chạy quá tốc độ|vượt tốc độ|tốc độ|lấn làn|vượt ẩu|vượt đường" +
            "|sử dụng điện thoại|dùng điện thoại|nghe điện thoại" +
            "|chở quá người|chở ba|chở bốn|chở hàng cồng kềnh" +
            "|không có xi nhanh|thiếu xi nhanh|không có còi|thiếu còi" +
            "|không có biển số|biển số giả|che biển số|thiếu biển số" +
            "|lùi xe|quay đầu xe|quay đầu|đi sai làn" +
            "|không thắt dây an toàn|không đội mũ|không có đăng kiểm)");

    // Broad pattern used as fallback when no specific violation matched
    private static final Pattern VIOLATION_BROAD_PATTERN = Pattern.compile(
            "(?i)(không có|thiếu|không mang|không đội|không bật|không gắn|không đăng|không thắt)" +
            "\\s+([\\p{L}\\s]{2,40})");
    private static final Pattern VIOLATION_KEYWORD_PATTERN = Pattern.compile(
            "(?i)(gương|đèn|còi|biển số|xi nhanh|đăng kiểm|bảo hiểm|đăng ký|cà vẹt|bằng lái|mũ bảo hiểm)");
    private static final Pattern ALCOHOL_PATTERN = Pattern.compile("(?i)(?:nồng độ cồn|uống rượu|uống bia|uống cồn|bia rượu|rượu bia|dùng bia|dùng rượu|có cồn|bia|rượu|cồn)(?:[^.!?\\n]{0,60}?(không|có|dương tính|âm tính|vượt mức))?");
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
        addMatch(facts, "vehicleType", VEHICLE_PATTERN.matcher(normalized), normalized);
        addMatch(facts, "violationType", VIOLATION_PATTERN.matcher(normalized), normalized);
        addMatch(facts, "alcoholStatus", ALCOHOL_PATTERN.matcher(normalized), normalized);
        addMatch(facts, "licenseStatus", LICENSE_PATTERN.matcher(normalized), normalized);
        addMatch(facts, "injuryStatus", INJURY_PATTERN.matcher(normalized), normalized);
        addMatch(facts, "documentStatus", DOCUMENT_PATTERN.matcher(normalized), normalized);

        // Broad fallback: if violationType still not captured, try "không có X" / "thiếu X" + keyword
        if (!facts.containsKey("violationType")) {
            Matcher broadMatcher = VIOLATION_BROAD_PATTERN.matcher(normalized);
            if (broadMatcher.find()) {
                String phrase = (broadMatcher.group(1) + " " + broadMatcher.group(2)).trim();
                // Only accept if the noun part contains a known traffic equipment keyword
                if (VIOLATION_KEYWORD_PATTERN.matcher(broadMatcher.group(2)).find()) {
                    facts.put("violationType", new ExtractedFact("violationType", cleanupValue(phrase)));
                }
            }
        }

        return List.copyOf(facts.values());
    }

    private void addMatch(Map<String, ExtractedFact> facts, String factKey, Matcher matcher, String fullText) {
        while (matcher.find()) {
            if (isPrecededByNegation(fullText, matcher.start())) {
                continue;
            }
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group();
            facts.put(factKey, new ExtractedFact(factKey, cleanupValue(value)));
        }
    }

    private boolean isPrecededByNegation(String text, int matchStart) {
        String prefix = text.substring(Math.max(0, matchStart - 30), matchStart).stripTrailing();
        return prefix.endsWith("không phải")
                || prefix.endsWith("không phải là")
                || prefix.endsWith("chứ không phải");
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
