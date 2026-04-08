package com.vn.traffic.chatbot.chat.service;

public final class AnswerCompositionPolicy {

    public static final String DEFAULT_DISCLAIMER = "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.";
    public static final String REFUSAL_MESSAGE = "Tôi chưa thể trả lời chắc chắn vì chưa tìm thấy đủ căn cứ đáng tin cậy trong nguồn pháp lý đã được phê duyệt. Bạn hãy nêu rõ hơn câu hỏi hoặc bổ sung bối cảnh để tôi tra cứu chính xác hơn.";
    public static final String LIMITED_NOTICE = "Một số nội dung dưới đây chỉ được trả lời trong phạm vi nguồn đã truy xuất được; các phần chưa đủ căn cứ sẽ được lược bỏ.";

    private AnswerCompositionPolicy() {
    }
}
