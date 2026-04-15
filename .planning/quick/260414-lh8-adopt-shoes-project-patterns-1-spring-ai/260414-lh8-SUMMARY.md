# 260414-lh8: Adopt Shoes Project Patterns (Part 1) Summary

Spring AI ChatMemory for threaded conversations, ResponseGeneral API envelope, BaseEntity/BaseAuditableEntity domain hierarchy.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Spring AI ChatMemory + MessageChatMemoryAdvisor | 68bef02 | ChatMemoryConfig.java, ChatService.java, ChatThreadService.java, 015-spring-ai-chat-memory.xml |
| 2 | ResponseGeneral wrapper for all APIs | b2deb99 | ResponseGeneral.java, all *Controller.java, frontend client.ts |
| 3 | BaseEntity + BaseAuditableEntity | 7381eb8 | BaseEntity.java, BaseAuditableEntity.java, all domain entities, 016-add-audit-columns.xml |

## Task Details

### Task 1: Spring AI ChatMemory + MessageChatMemoryAdvisor

- Created `ChatMemoryConfig` with `JdbcChatMemoryRepository` + `MessageWindowChatMemory` (max 10 messages)
- Added `ChatMemory` field to `ChatService` via constructor injection (Lombok `@RequiredArgsConstructor`)
- Added `answer(question, modelId, conversationId)` overload that attaches `MessageChatMemoryAdvisor` when conversationId is non-null
- Updated `ChatThreadService.createThread()` and `postMessage()` to pass `threadId.toString()` as conversationId instead of manually fetching history
- Kept existing `answer(question, modelId)` and `answer(question, modelId, List<ChatMessage>)` signatures unchanged
- Created Liquibase migration 015 for `SPRING_AI_CHAT_MEMORY` table with conversation_id+timestamp index and type check constraint
- Configured `spring.ai.chat.memory.repository.jdbc.initialize-schema: never` in application.yaml
- Updated all affected tests (ChatServiceTest, ChatFlowIntegrationTest, ChatScenarioAnalysisIntegrationTest, ChatThreadFlowIntegrationTest)

### Task 2: ResponseGeneral Wrapper for All APIs

- Created `ResponseGeneral<T>` with status, message, data, timestamp fields and static factory methods
- Wrapped all 10 controller classes (PublicChat, AllowedModels, ChatLog, CheckDef, CheckRun, ChunkAdmin, IngestionAdmin, AiParameterSet, SourceAdmin, TrustPolicyAdmin)
- Used `ofSuccess` for 200 responses, `ofCreated` for 201/202 responses
- Kept DELETE endpoints returning `ResponseEntity<Void>` with 204 (no body)
- Updated frontend `client.ts` with `ResponseGeneral<T>` interface, changed all methods to unwrap via `r.data.data`
- Updated all controller tests to assert `$.data.fieldName` instead of `$.fieldName`
- Updated direct-call tests (BatchIngestionControllerTest, TrustPolicyControllerTest) for ResponseGeneral body unwrapping

### Task 3: BaseEntity + BaseAuditableEntity Hierarchy

- Created `BaseEntity` with `@Id @GeneratedValue @UuidGenerator` UUID id field
- Created `BaseAuditableEntity` extending BaseEntity with createdBy, createdAt, updatedBy, updatedAt
- Refactored 5 entities to extend `BaseAuditableEntity`: KbSource, SourceTrustPolicy, AiParameterSet, CheckDef, ChatThread
- Refactored 8 entities to extend `BaseEntity`: ChatMessage, ChatLog, CheckRun, CheckResult, KbIngestionJob, KbSourceFetchSnapshot, KbSourceVersion, KbSourceApprovalEvent
- Replaced `@Builder` with `@SuperBuilder` on all entities for inheritance compatibility
- Added `@EqualsAndHashCode(callSuper = true)` to all entities
- Created Liquibase migration 016 adding created_by/updated_by columns to ai_parameter_set, check_def, chat_thread, source_trust_policy

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

1. ChatThread has no own fields after refactoring (all fields come from BaseAuditableEntity) - removed @AllArgsConstructor to avoid duplicate no-arg constructor
2. DELETE endpoints kept as ResponseEntity<Void> with 204 status (no ResponseGeneral wrapping since there is no body)
3. ChatLogAdminController list() method returns ResponseGeneral directly (not wrapped in ResponseEntity) since it was already returning Page directly

## Verification

All 3 tasks verified via `./gradlew compileJava compileTestJava` - BUILD SUCCESSFUL with only pre-existing deprecation warnings.
