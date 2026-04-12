package com.vn.traffic.chatbot.parameter.service;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.repo.AiParameterSetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiParameterSetServiceTest {

    @Mock
    private AiParameterSetRepository repository;

    @InjectMocks
    private AiParameterSetService service;

    @Test
    void createPersistsEntityWithNameAndContentReturnsWithUuid() {
        // given
        String name = "Test Parameter Set";
        String content = "model:\n  name: openai";

        AiParameterSet saved = AiParameterSet.builder()
                .id(UUID.randomUUID())
                .name(name)
                .content(content)
                .active(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(repository.save(any(AiParameterSet.class))).thenReturn(saved);

        // when
        AiParameterSet result = service.create(name, content, null, null);

        // then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getContent()).isEqualTo(content);
        assertThat(result.isActive()).isFalse();
        verify(repository).save(any(AiParameterSet.class));
    }

    @Test
    void activateSetsTargetActiveTrueAndAllOthersActiveFalse() {
        // given
        UUID id = UUID.randomUUID();
        AiParameterSet target = AiParameterSet.builder()
                .id(id)
                .name("Set A")
                .content("model:\n  name: openai")
                .active(false)
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(target));
        when(repository.deactivateAll()).thenReturn(1);
        when(repository.save(any(AiParameterSet.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        AiParameterSet result = service.activate(id);

        // then
        assertThat(result.isActive()).isTrue();
        verify(repository).deactivateAll();
        verify(repository).save(target);
    }

    @Test
    void copySetsNameWithSuffixAndActiveFalse() {
        // given
        UUID id = UUID.randomUUID();
        AiParameterSet original = AiParameterSet.builder()
                .id(id)
                .name("Bo tham so mac dinh")
                .content("model:\n  name: openai")
                .active(true)
                .build();

        AiParameterSet copied = AiParameterSet.builder()
                .id(UUID.randomUUID())
                .name("Bo tham so mac dinh (ban sao)")
                .content("model:\n  name: openai")
                .active(false)
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(original));
        when(repository.save(any(AiParameterSet.class))).thenReturn(copied);

        // when
        AiParameterSet result = service.copy(id);

        // then
        assertThat(result.getName()).isEqualTo("Bo tham so mac dinh (ban sao)");
        assertThat(result.isActive()).isFalse();
        assertThat(result.getContent()).isEqualTo(original.getContent());
    }

    @Test
    void deleteThrowsIfEntityIsActive() {
        // given
        UUID id = UUID.randomUUID();
        AiParameterSet active = AiParameterSet.builder()
                .id(id)
                .name("Active Set")
                .content("model:\n  name: openai")
                .active(true)
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(active));

        // when/then
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cannot delete active parameter set");

        verify(repository, never()).delete(any());
    }

    @Test
    void findAllReturnsAllOrderedByCreatedAtDesc() {
        // given
        AiParameterSet set1 = AiParameterSet.builder()
                .id(UUID.randomUUID())
                .name("Set 1")
                .content("content1")
                .active(false)
                .build();
        AiParameterSet set2 = AiParameterSet.builder()
                .id(UUID.randomUUID())
                .name("Set 2")
                .content("content2")
                .active(true)
                .build();

        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(set2, set1));

        // when
        List<AiParameterSet> result = service.findAll();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Set 2");
        verify(repository).findAllByOrderByCreatedAtDesc();
    }
}
