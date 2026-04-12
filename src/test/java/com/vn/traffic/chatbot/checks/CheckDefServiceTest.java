package com.vn.traffic.chatbot.checks;

import com.vn.traffic.chatbot.checks.api.dto.CheckDefRequest;
import com.vn.traffic.chatbot.checks.domain.CheckDef;
import com.vn.traffic.chatbot.checks.repo.CheckDefRepository;
import com.vn.traffic.chatbot.checks.service.CheckDefService;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckDefServiceTest {

    @Mock
    private CheckDefRepository checkDefRepository;

    private CheckDefService checkDefService;

    @BeforeEach
    void setUp() {
        checkDefService = new CheckDefService(checkDefRepository);
    }

    @Test
    void testCreateCheckDef() {
        CheckDefRequest request = new CheckDefRequest(
                "Tốc độ tối đa trong khu dân cư là bao nhiêu?",
                "Tốc độ tối đa trong khu dân cư là 50 km/h.",
                "traffic-speed",
                true
        );

        CheckDef saved = CheckDef.builder()
                .id(UUID.randomUUID())
                .question(request.question())
                .referenceAnswer(request.referenceAnswer())
                .category(request.category())
                .active(true)
                .build();

        when(checkDefRepository.save(any(CheckDef.class))).thenReturn(saved);

        CheckDef result = checkDefService.create(request);

        assertThat(result.getQuestion()).isEqualTo(request.question());
        assertThat(result.getReferenceAnswer()).isEqualTo(request.referenceAnswer());
    }

    @Test
    void testUpdateCheckDef() {
        UUID id = UUID.randomUUID();
        CheckDef existing = CheckDef.builder()
                .id(id)
                .question("Câu hỏi ban đầu dài hơn mười ký tự.")
                .referenceAnswer("Câu trả lời tham chiếu ban đầu dài hơn mười ký tự.")
                .active(true)
                .build();

        CheckDefRequest request = new CheckDefRequest(
                "Câu hỏi mới về quy tắc giao thông đường bộ?",
                "Câu trả lời mới về quy tắc giao thông đường bộ Việt Nam.",
                "traffic-rules",
                true
        );

        CheckDef updated = CheckDef.builder()
                .id(id)
                .question(request.question())
                .referenceAnswer(request.referenceAnswer())
                .category(request.category())
                .active(true)
                .build();

        when(checkDefRepository.findById(id)).thenReturn(Optional.of(existing));
        when(checkDefRepository.save(any(CheckDef.class))).thenReturn(updated);

        CheckDef result = checkDefService.update(id, request);

        assertThat(result.getQuestion()).isEqualTo(request.question());
    }

    @Test
    void testDeleteCheckDef() {
        UUID id = UUID.randomUUID();
        when(checkDefRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> checkDefService.delete(id))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void testFindActiveCheckDefs() {
        CheckDef def1 = CheckDef.builder().id(UUID.randomUUID()).active(true).build();
        CheckDef def2 = CheckDef.builder().id(UUID.randomUUID()).active(true).build();
        when(checkDefRepository.findByActiveTrue()).thenReturn(List.of(def1, def2));

        List<CheckDef> result = checkDefService.findActive();

        assertThat(result).hasSize(2);
    }
}
