package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Policy component that provides configurable AI answer composition messages.
 *
 * <p>All messages read from the active parameter set at runtime via {@link ActiveParameterSetProvider},
 * falling back to the static FALLBACK constants if no active parameter set is configured or if
 * the parameter set YAML does not contain the expected key.
 *
 * <p>The static constants remain accessible for use in tests and for other classes that
 * compare against expected values.
 */
@Component
@RequiredArgsConstructor
public class AnswerCompositionPolicy {

    // Static constants kept for backward compatibility (tests, comparisons) and as fallback values.
    public static final String DEFAULT_DISCLAIMER = "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.";
    public static final String REFUSAL_MESSAGE = "Tôi chưa thể trả lời chắc chắn vì chưa tìm thấy đủ căn cứ đáng tin cậy trong nguồn pháp lý đã được phê duyệt. Bạn hãy nêu rõ hơn câu hỏi hoặc bổ sung bối cảnh để tôi tra cứu chính xác hơn.";
    public static final String LIMITED_NOTICE = "Một số nội dung dưới đây chỉ được trả lời trong phạm vi nguồn đã truy xuất được; các phần chưa đủ căn cứ sẽ được lược bỏ.";
    public static final String REFUSAL_NEXT_STEP_NARROW_SCOPE = "Nêu rõ hành vi vi phạm, loại phương tiện, thời gian hoặc địa điểm để thu hẹp phạm vi tra cứu.";
    public static final String REFUSAL_NEXT_STEP_NAME_DOCUMENT = "Nếu bạn đang hỏi về giấy tờ hoặc thủ tục, hãy ghi rõ tên giấy tờ, quyết định hoặc bước xử lý cần kiểm tra.";
    public static final String REFUSAL_NEXT_STEP_VERIFY_SOURCE = "Ưu tiên đối chiếu thêm với văn bản hoặc cổng thông tin chính thức mà bạn đang áp dụng.";

    private final ActiveParameterSetProvider paramProvider;

    /**
     * Returns the disclaimer message, reading from the active parameter set with fallback.
     */
    public String getDisclaimer() {
        return paramProvider.getString("messages.disclaimer", DEFAULT_DISCLAIMER);
    }

    /**
     * Returns the refusal message, reading from the active parameter set with fallback.
     */
    public String getRefusalMessage() {
        return paramProvider.getString("messages.refusal", REFUSAL_MESSAGE);
    }

    /**
     * Returns the limited-notice message, reading from the active parameter set with fallback.
     */
    public String getLimitedNotice() {
        return paramProvider.getString("messages.limitedNotice", LIMITED_NOTICE);
    }

    /**
     * Returns the list of refusal next-step suggestions, reading from the active parameter set.
     */
    public List<String> getRefusalNextSteps() {
        return List.of(
                paramProvider.getString("messages.refusalNextStep1", REFUSAL_NEXT_STEP_NARROW_SCOPE),
                paramProvider.getString("messages.refusalNextStep2", REFUSAL_NEXT_STEP_NAME_DOCUMENT),
                paramProvider.getString("messages.refusalNextStep3", REFUSAL_NEXT_STEP_VERIFY_SOURCE)
        );
    }
}
