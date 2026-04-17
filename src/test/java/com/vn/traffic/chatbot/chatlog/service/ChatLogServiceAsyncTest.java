package com.vn.traffic.chatbot.chatlog.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PERF-03 RED tests — D-10 / D-11 / Pitfall A / Pitfall B.
 *
 * <p>Three assertions:
 * <ol>
 *   <li>{@code asyncSnapshotImmutability} — pure JDK; GREEN on commit. Confirms the
 *       snapshot pattern {@code List.copyOf(...)} the caller must apply before handing
 *       off to {@code @Async} (Pitfall 7 guard).</li>
 *   <li>{@code saveMethodCarriesAsyncAnnotation} — reflection assertion on
 *       {@code ChatLogService#save}. RED today; GREEN after Plan 07-03 Task 1.</li>
 *   <li>{@code saveMethodCarriesRequiresNewPropagation} — reflection assertion for
 *       {@code @Transactional(propagation = REQUIRES_NEW)}. RED today; GREEN after
 *       Plan 07-03 Task 1.</li>
 * </ol>
 *
 * <p>Kept as pure reflection/JDK — NO {@code @SpringBootTest}. A deeper integration
 * smoke is deferred to manual (VALIDATION.md manual-only row). Per D-07, if a future
 * Spring-AI-adjacent integration test is added, consult Context7
 * {@code /spring-projects/spring-boot} task-execution docs first.
 */
class ChatLogServiceAsyncTest {

    @Test
    void asyncSnapshotImmutability() {
        List<String> logMessages = new ArrayList<>();
        logMessages.add("step 1");
        logMessages.add("step 2");

        // D-11: snapshot BEFORE async handoff.
        String snapshot = String.join("\n", List.copyOf(logMessages));

        // Mutate the source after the snapshot is taken (simulates the caller's
        // subsequent logger.accept(...) calls that happen post-handoff).
        logMessages.add("step 3 added after snapshot");
        logMessages.set(0, "MUTATED");

        assertThat(snapshot).isEqualTo("step 1\nstep 2");
        assertThat(snapshot).doesNotContain("MUTATED");
        assertThat(snapshot).doesNotContain("step 3 added after snapshot");
    }

    @Test
    void saveMethodCarriesAsyncAnnotation() throws NoSuchMethodException {
        Method save = ChatLogService.class.getMethod(
                "save",
                String.class,
                ChatAnswerResponse.class,
                GroundingStatus.class,
                String.class,
                int.class,
                int.class,
                int.class,
                String.class);

        assertThat(save.isAnnotationPresent(Async.class))
                .as("ChatLogService.save must be @Async (PERF-03a / D-10)")
                .isTrue();
        Async async = save.getAnnotation(Async.class);
        assertThat(async.value())
                .as("@Async qualifier must match ChatLogAsyncConfig.CHAT_LOG_EXECUTOR")
                .isEqualTo("chatLogExecutor");
    }

    @Test
    void saveMethodCarriesRequiresNewPropagation() throws NoSuchMethodException {
        Method save = ChatLogService.class.getMethod(
                "save",
                String.class,
                ChatAnswerResponse.class,
                GroundingStatus.class,
                String.class,
                int.class,
                int.class,
                int.class,
                String.class);

        assertThat(save.isAnnotationPresent(Transactional.class))
                .as("ChatLogService.save must be @Transactional (PERF-03c / D-10)")
                .isTrue();
        Transactional tx = save.getAnnotation(Transactional.class);
        assertThat(tx.propagation())
                .as("@Transactional propagation must be REQUIRES_NEW (Pitfall B guard)")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
