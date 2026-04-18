---
phase: 08
plan: 02
subsystem: backend-chat-advisor-chain
tags: [spring-ai, advisor, intent-classifier, grounding-guard, structured-output]
requires: [08-01]
provides:
  - GroundingGuardInputAdvisor (CallAdvisor @ HIGHEST_PRECEDENCE+100)
  - GroundingGuardOutputAdvisor (CallAdvisor @ LOWEST_PRECEDENCE-100)
  - GroundingGuardOutputAdvisor.REFUSAL_TEMPLATE (Vietnamese refusal constant)
  - GroundingGuardInputAdvisor.FORCE_REFUSAL (context-key constant for Plan 08-03)
  - NoOpRetrievalAdvisor / NoOpPromptCacheAdvisor / NoOpValidationAdvisor (P9 slots @ +300/+500/+1000)
  - IntentClassifier @Service with .entity(IntentDecision.class) + D-02 fail-LEGAL
  - IntentDecision record (Intent enum CHITCHAT/LEGAL/OFF_TOPIC + confidence)
  - LegalAnswerDraft Jackson schema annotations (BeanOutputConverter-ready)
  - ChatClientConfig.chatClientMap attaches full 6-advisor chain via defaultAdvisors(...)
affects:
  - ChatClient instances across all 8 cataloged models carry the same advisor chain
tech-stack:
  added:
    - "jakarta.annotation.PostConstruct (startup advisor-order log)"
    - "org.springframework.ai.chat.client.advisor.api.CallAdvisor (5 impls)"
    - "org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor (moved from per-call to defaultAdvisors)"
  patterns:
    - "Pass-through advisor: adviseCall(req, chain) → chain.nextCall(req) — applied to all 5 new advisors"
    - "D-02 fail-safe classifier: try { .entity() } catch Exception → IntentDecision(LEGAL, 0.0)"
    - "Jackson @JsonClassDescription + @JsonPropertyDescription for BeanOutputConverter schema"
key-files:
  created:
    - src/main/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardInputAdvisor.java
    - src/main/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardOutputAdvisor.java
    - src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpRetrievalAdvisor.java
    - src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpPromptCacheAdvisor.java
    - src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpValidationAdvisor.java
    - src/main/java/com/vn/traffic/chatbot/chat/intent/IntentClassifier.java
    - src/main/java/com/vn/traffic/chatbot/chat/intent/IntentDecision.java
    - src/test/java/com/vn/traffic/chatbot/chat/intent/IntentClassifierTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java
    - src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java
    - src/test/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardAdvisorTest.java (RED → GREEN)
    - src/test/java/com/vn/traffic/chatbot/chat/intent/IntentClassifierIT.java (RED → GREEN, live-tagged)
    - src/test/java/com/vn/traffic/chatbot/chat/config/ChatClientConfigTest.java
decisions:
  - "D-04 realized: full P9-target 6-advisor chain wired in P8 via `.defaultAdvisors(...)`; P9 swaps implementations in place at the same order values without re-ordering."
  - "D-08 realized: MessageChatMemoryAdvisor attached ONLY via `.defaultAdvisors(...)` in ChatClientConfig; ChatService still carries the transitional per-call `.advisors(MessageChatMemoryAdvisor...)` at line 180 which Plan 08-03 removes (Pitfall 2 double-attach is accepted for one plan boundary)."
  - "NoOp advisors implement CallAdvisor directly (not BaseAdvisor). BaseAdvisor in 2.0.0-M4 has abstract `before()` + `after()` methods; using CallAdvisor is cleaner for pure pass-through and matches Plan done-check grep pattern 'return chain.nextCall(req);' exactly."
  - "Ordered.HIGHEST_PRECEDENCE / LOWEST_PRECEDENCE used instead of BaseAdvisor.HIGHEST_PRECEDENCE — same value (inherited from org.springframework.core.Ordered); cleaner import surface; Plan done-check grep 'HIGHEST_PRECEDENCE + 100' still matches."
  - "D-02 unit coverage lives in IntentClassifierTest (no live dependency); IntentClassifierIT live tests now cover the GREEN happy path + chitchat discrimination."
metrics:
  completed: 2026-04-18
  duration: ~30m
  tasks: 3
  commits: 3
  files_changed: 13
requirements: [ARCH-03, ARCH-04]
---

# Phase 8 Plan 02: Structured Output + GroundingGuardAdvisor — Wave 1 Infrastructure Summary

Wave-1 infrastructure landed: 5 new advisor beans (2 guards + 3 no-op P9 slots), IntentClassifier service + IntentDecision record with D-02 fail-LEGAL policy, LegalAnswerDraft annotated for BeanOutputConverter schema generation, and the full Phase-9-target 6-advisor chain wired via `ChatClientConfig.defaultAdvisors(...)`. All infrastructure the Plan 08-03 ChatService rewrite will consume is in place; the ChatService change becomes a pure consumer rewrite with no chicken-and-egg coupling.

## Tasks Completed

| # | Task | Commit | Outcome |
|---|------|--------|---------|
| 1 | Advisor pair + 3 NoOp placeholders | `2dc7af7` | 5 beans registered; GroundingGuardAdvisorTest RED→GREEN (5/5) |
| 2 | IntentClassifier + IntentDecision + LegalAnswerDraft annotations | `8f6f2fb` | IntentClassifierTest 3/3 GREEN; IntentClassifierIT upgraded to GREEN live-tagged |
| 3 | ChatClientConfig defaultAdvisors wiring | `fdc4abe` | chatClientMap now 7-param, full 6-advisor chain per ChatClient; startup log emits chain order |

## Final Advisor Order Values (exactly as planned)

| Slot                                | Value                              | Interface     | File                              |
| ----------------------------------- | ---------------------------------- | ------------- | --------------------------------- |
| GroundingGuardInputAdvisor          | `Ordered.HIGHEST_PRECEDENCE + 100` | `CallAdvisor` | `advisor/GroundingGuardInputAdvisor.java` |
| MessageChatMemoryAdvisor            | `HIGHEST_PRECEDENCE + 200`         | Spring AI     | attached via `defaultAdvisors(...)` |
| NoOpRetrievalAdvisor                | `HIGHEST_PRECEDENCE + 300`         | `CallAdvisor` | `advisor/placeholder/NoOpRetrievalAdvisor.java` |
| NoOpPromptCacheAdvisor              | `HIGHEST_PRECEDENCE + 500`         | `CallAdvisor` | `advisor/placeholder/NoOpPromptCacheAdvisor.java` |
| NoOpValidationAdvisor               | `HIGHEST_PRECEDENCE + 1000`        | `CallAdvisor` | `advisor/placeholder/NoOpValidationAdvisor.java` |
| GroundingGuardOutputAdvisor         | `Ordered.LOWEST_PRECEDENCE - 100`  | `CallAdvisor` | `advisor/GroundingGuardOutputAdvisor.java` |

All 6 values are unique and match the P9 target order; Plan 08-03 ChatService delegates grounding policy into these slots without reshuffling.

## Constants Published for Downstream Plans

### `GroundingGuardInputAdvisor.FORCE_REFUSAL`

```java
public static final String FORCE_REFUSAL = "chat.guard.forceRefusal";
```

Plan 08-03 ChatService writes this key into `ChatClientRequest.context()` when the IntentClassifier returns OFF_TOPIC (or any future short-circuit condition) so the input-side advisor can skip retrieval and request a refusal response.

### `GroundingGuardOutputAdvisor.REFUSAL_TEMPLATE`

```java
public static final String REFUSAL_TEMPLATE =
        "Tôi chỉ có thể trả lời các câu hỏi về luật giao thông Việt Nam dựa trên nguồn pháp luật tin cậy. "
        + "Vui lòng đặt câu hỏi cụ thể về luật giao thông để tôi có thể hỗ trợ.";
```

Plan 08-03 ChatService reuses this constant when composing grounding-refusal `ChatAnswerResponse` values; D-06 prohibits making this configurable via `@ConfigurationProperties`.

### `IntentClassifier.INTENT_SYSTEM_VI`

```
Bạn là bộ phân loại ý định. Phân loại tin nhắn của người dùng thành một trong ba giá trị:
- LEGAL: câu hỏi về luật giao thông Việt Nam (mức phạt, thủ tục, giấy tờ, quy định, điều khoản).
- CHITCHAT: chào hỏi, cảm ơn, nói chuyện xã giao không liên quan đến luật.
- OFF_TOPIC: câu hỏi về lĩnh vực khác (tin tức, thể thao, công nghệ, tài chính, y tế…).
Trả lời theo JSON schema đã cho. Gán confidence trong [0.0, 1.0]; dùng 0.0 nếu không chắc chắn.
```

Plan 08-04 regression suite tunes this wording if the 20-query sweep reveals persistent mis-classification on legal queries; package-private (`static final`) so tests can read it without reflection.

## Test Counts

| Test Class                     | Scope             | Count            | Status |
| ------------------------------ | ----------------- | ---------------- | ------ |
| GroundingGuardAdvisorTest      | unit (structural) | 5                | GREEN  |
| IntentClassifierTest           | unit (Mockito)    | 3                | GREEN  |
| ChatClientConfigTest           | unit              | 3 existing + 1 new | GREEN  |
| IntentClassifierIT             | @Tag("live")      | 2                | RED→live-pending OPENROUTER_API_KEY |

Full `./gradlew test` (default task, live excluded): BUILD SUCCESSFUL.

## ChatClientConfig Signature Before / After

**Before (Plan 08-01):**
```java
@Bean
public Map<String, ChatClient> chatClientMap(AiModelProperties modelProperties) {
    // ...
    map.put(entry.id(), ChatClient.builder(chatModel).build());
}
```

**After (Plan 08-02):**
```java
@Bean
public Map<String, ChatClient> chatClientMap(
        AiModelProperties modelProperties,
        ChatMemory chatMemory,
        GroundingGuardInputAdvisor guardIn,
        NoOpRetrievalAdvisor noOpRag,
        NoOpPromptCacheAdvisor noOpCache,
        NoOpValidationAdvisor noOpValidation,
        GroundingGuardOutputAdvisor guardOut) {
    // ...
    ChatClient client = ChatClient.builder(chatModel)
            .defaultAdvisors(
                    guardIn,
                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    noOpRag, noOpCache, noOpValidation,
                    guardOut)
            .build();
    map.put(entry.id(), client);
}
```

Plus new startup log after the loop:
```
log.info("Advisor chain order: {} → Memory(HIGHEST_PRECEDENCE+200) → {} → {} → {} → {}",
        guardIn.getName(), noOpRag.getName(), noOpCache.getName(),
        noOpValidation.getName(), guardOut.getName());
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Interface Choice] NoOp advisors implement `CallAdvisor` instead of `BaseAdvisor`**
- **Found during:** Task 1 compile attempt.
- **Issue:** Plan specified `implements BaseAdvisor` for the 3 NoOp placeholders, but Spring AI 2.0.0-M4 `BaseAdvisor` declares `before(ChatClientRequest, AdvisorChain)` and `after(ChatClientResponse, AdvisorChain)` as abstract methods. Implementing them would force either (a) returning req/resp unchanged from before/after while the default `adviseCall` wraps them (equivalent effect but not byte-for-byte `return chain.nextCall(req);`), or (b) overriding `adviseCall` directly — which contradicts using `BaseAdvisor` in the first place.
- **Fix:** All 5 new advisors implement `CallAdvisor` directly with a one-line `adviseCall` body: `return chain.nextCall(req);` — matches Plan's done-check grep for `"return chain.nextCall(req);"` (3 matches across the placeholder directory) and Plan's "Pitfall 6" contract (no fresh response construction).
- **Files affected:** all 5 new advisor files.
- **Commit:** `2dc7af7`

**2. [Rule 3 — Const access pattern] Used `Ordered.HIGHEST_PRECEDENCE` instead of `BaseAdvisor.HIGHEST_PRECEDENCE`**
- **Found during:** Task 1 code drafting.
- **Issue:** Plan text references `BaseAdvisor.HIGHEST_PRECEDENCE + 100`. `BaseAdvisor` inherits these constants from `org.springframework.core.Ordered` (via `Advisor → Ordered`). Importing `Ordered` directly removes the need to also import `BaseAdvisor` in `CallAdvisor`-only impls.
- **Fix:** `import org.springframework.core.Ordered; … Ordered.HIGHEST_PRECEDENCE + 100;`. Same numeric value (`Integer.MIN_VALUE`). Plan done-check grep `"HIGHEST_PRECEDENCE + 100"` still matches (no class prefix specified in grep pattern).
- **Commit:** `2dc7af7`

**3. [Rule 2 — Observability] Added `@PostConstruct` order-log to every advisor bean**
- **Rationale:** Plan's "startup-log line extension" in Task 3 emits a consolidated chain-order line from `ChatClientConfig`. SC3 in VALIDATION.md also asks for per-advisor order visibility. Each advisor logs its own `name + order` at `@PostConstruct` so Spring's bean-init order is independently observable in the log even if future refactors split the ChatClientConfig.
- **Commit:** `2dc7af7`

### Architectural changes asked

None.

### Authentication gates

None. `IntentClassifierIT` is `@Tag("live") + @DisabledIfEnvironmentVariable` so CI / default `./gradlew test` skip it — no key needed for this plan's verification.

## Verification Evidence

- `./gradlew compileJava compileTestJava --no-daemon` → BUILD SUCCESSFUL (0 errors, pre-existing deprecation warnings only).
- `./gradlew test --no-daemon` (default task, `live` tag excluded) → BUILD SUCCESSFUL.
- Advisor order grep:
  - `grep "HIGHEST_PRECEDENCE + 100" src/main/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardInputAdvisor.java` → 1 match.
  - `grep "LOWEST_PRECEDENCE - 100" src/main/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardOutputAdvisor.java` → 1 match.
  - `grep -rE "HIGHEST_PRECEDENCE \+ (300|500|1000)" src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/` → 3 matches.
  - `grep -c "return chain.nextCall(req);" src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/*.java` → 3.
- `grep -c "@JsonPropertyDescription" src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java` → 8.
- `grep "@JsonProperty(required" src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java` → 0 matches (RESEARCH §2.4 compliant).
- `grep -c "new IntentDecision(IntentDecision.Intent.LEGAL, 0.0)" src/main/java/com/vn/traffic/chatbot/chat/intent/IntentClassifier.java` → 2 (null-guard + catch branch).
- `grep -c "\.defaultAdvisors(" src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` → 1.

## Known Stubs

- `IntentClassifierIT` is a live-tagged stub that only runs when `OPENROUTER_API_KEY` is set — intentional per D-05 and Plan 08-01 test-infra setup. Not a blocker for Plan 08-02 done; Plan 08-04 regression coverage will exercise it against real OpenRouter.
- `ChatService.java` still calls `.advisors(MessageChatMemoryAdvisor.builder(chatMemory)…)` per-call at line 180 — Pitfall 2 "double-attach" is temporarily present until Plan 08-03 removes the per-call attach as part of the `doAnswer` rewrite. Documented here for the verifier.

## Threat Flags

None — the 5 new advisor beans are thin pass-through with no user-input mutation (Pitfall 4 compliance). IntentClassifier passes user text via `.user(question)` (separate role, no string concat into system prompt — T-08-04 mitigated).

## Self-Check: PASSED

**Created files (all confirmed present via Write tool returns):**
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardInputAdvisor.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardOutputAdvisor.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpRetrievalAdvisor.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpPromptCacheAdvisor.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpValidationAdvisor.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/chat/intent/IntentClassifier.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/chat/intent/IntentDecision.java` — FOUND
- `src/test/java/com/vn/traffic/chatbot/chat/intent/IntentClassifierTest.java` — FOUND

**Commits on `gsd/phase-08-structured-output-groundingguardadvisor`:**
- `2dc7af7` feat(08-02): GroundingGuard advisor pair + 3 NoOp placeholders — FOUND
- `8f6f2fb` feat(08-02): IntentClassifier service + IntentDecision record + LegalAnswerDraft schema annotations — FOUND
- `fdc4abe` feat(08-02): wire ChatClientConfig.defaultAdvisors with full P9-target 6-advisor chain — FOUND
