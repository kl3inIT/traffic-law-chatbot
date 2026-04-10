package com.vn.traffic.chatbot.chat.service;

import java.util.List;
import java.util.Set;

public final class PromptSectionRules {

    public static final List<String> SECTION_ORDER = List.of(
            "Kết luận",
            "Căn cứ pháp lý",
            "Mức phạt hoặc hậu quả",
            "Giấy tờ hoặc thủ tục",
            "Các bước nên làm tiếp",
            "Lưu ý"
    );

    public static final Set<String> SUPPORTED_SECTION_NAMES = Set.of(
            "legalBasis",
            "penalties",
            "requiredDocuments",
            "procedureSteps",
            "nextSteps"
    );

    private PromptSectionRules() {
    }
}
