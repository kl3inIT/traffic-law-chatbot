# Coding Conventions

**Analysis Date:** 2026-04-11

## Naming Patterns

**Files:**
- Backend main code uses one public top-level type per `PascalCase.java` file under feature-first packages such as `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`, `src/main/java/com/vn/traffic/chatbot/source/api/dto/CreateSourceRequest.java`, and `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java`.
- Backend packages stay lowercase and feature-scoped: `chat.api`, `chat.service`, `chat.domain`, `chat.repo`, `source.api.dto`, `common.config`, `common.error`.
- Frontend files are mostly kebab-case and grouped by role, for example `frontend/components/chat/message-bubble.tsx`, `frontend/components/layout/app-sidebar.tsx`, `frontend/hooks/use-chat.ts`, and `frontend/lib/api/client.ts`.
- Next App Router reserved filenames stay framework-driven in `frontend/app/**`: `frontend/app/layout.tsx`, `frontend/app/error.tsx`, `frontend/app/loading.tsx`, `frontend/app/(chat)/page.tsx`.

**Functions:**
- Java methods use lower camelCase verbs such as `createSource`, `attachScenarioContext`, `submitUrl`, and `getRetrievalReadinessCounts` in `src/main/java/com/vn/traffic/chatbot/**`.
- React hooks use `useX` exports from `use-x.ts` files, for example `frontend/hooks/use-chat.ts`, `frontend/hooks/use-sources.ts`, and `frontend/hooks/use-parameters.ts`.
- React components use `PascalCase` names such as `AppSidebar`, `AddSourceDialog`, and `ParameterDialog` in `frontend/components/**`.

**Variables:**
- Java constructor-injected fields use descriptive lower camelCase names such as `sourceRepo`, `approvalEventRepo`, and `chunkInspectionService` in `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java` and `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`.
- Short-lived Java locals frequently use `var` when the type is obvious from the right-hand side, especially in controllers and services such as `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`.
- Frontend state and query client variables follow lower camelCase naming such as `queryClient`, `uploadMutation`, `isPending`, and `editTarget` in `frontend/components/admin/**` and `frontend/hooks/**`.

**Types:**
- Backend HTTP DTOs are usually Java `record`s with `Request` and `Response` suffixes, for example `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatQuestionRequest.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/dto/ChunkDetailResponse.java`, and `src/main/java/com/vn/traffic/chatbot/common/api/PageResponse.java`.
- Backend persistence models are mutable JPA entities with domain nouns such as `KbSource`, `AiParameterSet`, `ChatThread`, and `ThreadFact` in `src/main/java/com/vn/traffic/chatbot/**/domain/*.java`.
- Frontend contract types mirror backend names inside `frontend/types/api.ts`; keep new API contracts aligned there first, then consume them from `frontend/lib/api/*.ts` and `frontend/hooks/*.ts`.

## Layering Rules

**Backend feature layering:**
- Put HTTP controllers in `src/main/java/com/vn/traffic/chatbot/<feature>/api`, request and response DTOs in `src/main/java/com/vn/traffic/chatbot/<feature>/api/dto`, business logic in `src/main/java/com/vn/traffic/chatbot/<feature>/service`, persistence interfaces in `src/main/java/com/vn/traffic/chatbot/<feature>/repo`, and JPA entities and enums in `src/main/java/com/vn/traffic/chatbot/<feature>/domain`.
- Shared cross-cutting code belongs under `src/main/java/com/vn/traffic/chatbot/common/api`, `src/main/java/com/vn/traffic/chatbot/common/config`, and `src/main/java/com/vn/traffic/chatbot/common/error`.
- Keep controller methods thin. Map HTTP payloads to service calls and DTO responses in the controller or mapper layer as shown by `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`, `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, and `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java`.
- Service and orchestrator classes are the place for business rules and multi-step workflows, for example `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, and `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`.

**Frontend layering:**
- Keep route shells in `frontend/app/**`, feature views in `frontend/components/<feature>`, shared primitives in `frontend/components/ui`, stateful data hooks in `frontend/hooks`, transport helpers in `frontend/lib/api`, and contract types in `frontend/types/api.ts`.
- Centralize cache key definitions in `frontend/lib/query-keys.ts` and reuse them from hooks and invalidation paths such as `frontend/hooks/use-chat.ts` and `frontend/hooks/use-sources.ts`.
- Prefer route-group organization instead of feature barrels. `frontend/app/(chat)/**` owns chat routes and `frontend/app/(admin)/**` owns admin routes.

## DTO and Entity Patterns

**DTOs:**
- Use immutable Java `record` DTOs for JSON input and output. Examples: `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java`, `src/main/java/com/vn/traffic/chatbot/source/api/dto/SourceSummaryResponse.java`, and `src/main/java/com/vn/traffic/chatbot/parameter/api/dto/CreateAiParameterSetRequest.java`.
- Attach validation annotations directly to record components, not in controller code, as shown in `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatQuestionRequest.java`, `src/main/java/com/vn/traffic/chatbot/chat/api/dto/CreateChatThreadRequest.java`, and `src/main/java/com/vn/traffic/chatbot/source/api/dto/CreateSourceRequest.java`.
- Exception: multipart metadata uses a mutable bean with getters and setters in `src/main/java/com/vn/traffic/chatbot/ingestion/api/dto/UploadSourceRequest.java` because it is bound from `@RequestPart`.

**Entities:**
- JPA entities consistently use `@Entity`, `@Table`, UUID identifiers, and audit timestamps in files such as `src/main/java/com/vn/traffic/chatbot/source/domain/KbSource.java`, `src/main/java/com/vn/traffic/chatbot/parameter/domain/AiParameterSet.java`, and `src/main/java/com/vn/traffic/chatbot/chat/domain/ChatThread.java`.
- Lombok entity shape is `@Data`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor`; default state is expressed with `@Builder.Default` where needed in `src/main/java/com/vn/traffic/chatbot/source/domain/KbSource.java`, `src/main/java/com/vn/traffic/chatbot/source/domain/KbSourceVersion.java`, and `src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFact.java`.
- Conversion from entity to API DTO is kept near the boundary through private mapper helpers in controllers or dedicated mapping components such as `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java`.

## Code Style

**Formatting:**
- Backend has no checked-in formatter or style gate in `build.gradle`; preserve the existing 4-space indentation, blank-line spacing, and surrounding import order already used in `src/main/java/com/vn/traffic/chatbot/**`.
- Frontend formatting is defined in `frontend/.prettierrc`: semicolons enabled, single quotes, trailing commas, `printWidth` 100, `tabWidth` 2, and Tailwind class sorting through `prettier-plugin-tailwindcss`.
- Generated or imported UI primitives in `frontend/components/ui/*.tsx` and `frontend/components/ai-elements/*.tsx` keep their local style, including some double-quoted imports and props. Match the file you are editing instead of normalizing adjacent generated code.

**Linting:**
- Frontend linting is active through `frontend/eslint.config.mjs`, which extends `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`.
- There is no checked-in backend Checkstyle, PMD, Spotless, SpotBugs, or JaCoCo configuration in the repository root. Backend quality enforcement is currently compilation plus tests.

## Import Organization

**Order:**
1. Backend files commonly place project imports first, framework and Lombok imports next, and `java.*` imports last, as seen in `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java` and `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`.
2. Frontend files place package imports first, then `@/` alias imports, then relative imports or type-only imports, as seen in `frontend/hooks/use-chat.ts`, `frontend/components/layout/providers.tsx`, and `frontend/components/chat/message-bubble.tsx`.
3. Java tests keep static assertions and Mockito helpers at the bottom. Vitest suites place `vi.mock(...)` near the top before importing the component under test, as shown by `frontend/__tests__/app-sidebar.test.tsx`.

**Path Aliases:**
- Frontend uses the `@/*` alias from `frontend/tsconfig.json` and `frontend/components.json`.
- Prefer `@/components`, `@/hooks`, `@/lib`, and `@/types` imports over deep relative paths in `frontend/**`.

## Error Handling

**Patterns:**
- Domain and service failures use `AppException` plus `ErrorCode`, for example in `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`, and `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java`.
- HTTP error translation is centralized in `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java`, which returns `ProblemDetail` objects and attaches either `properties.errors` or `properties.errorCode`.
- Keep business-rule checks inside services even when HTTP request validation already ran. Examples: source approval state checks in `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java` and ingestion retry status checks in `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`.
- Prefer explicit safe fallbacks over leaked exceptions when external AI output is malformed. `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` logs parse failures and returns a structured fallback draft instead of surfacing raw model errors to callers.

## Validation

**Backend validation:**
- Apply `@Valid` at controller boundaries in `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java`, `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`, `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, and `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`.
- Put field-level validation on DTOs with Jakarta annotations such as `@NotBlank`, `@NotNull`, and `@Size`, as shown by `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatThreadMessageRequest.java`, `src/main/java/com/vn/traffic/chatbot/parameter/api/dto/UpdateAiParameterSetRequest.java`, and `src/main/java/com/vn/traffic/chatbot/ingestion/api/dto/UrlSourceRequest.java`.
- Keep non-DTO validation imperative in the service layer for stateful or file-based rules. `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java` validates upload emptiness and title content manually.

**Frontend validation:**
- Complex forms use `react-hook-form` plus `zod` inside the component that owns the dialog or page, for example `frontend/components/admin/parameters/parameter-dialog.tsx` and `frontend/app/(admin)/parameters/page.tsx`.
- Simpler dialogs use local state plus synchronous guards inside the mutation function, for example `frontend/components/admin/sources/add-source-dialog.tsx`.
- Inline form errors are rendered directly from component state or `form.formState.errors`; there is no shared client-side validation abstraction outside the form owner.

## Logging

**Framework:** Lombok `@Slf4j` on the backend, `console.error` only on the frontend.

**Patterns:**
- Use `log.info(...)` for explicit admin actions or successful one-off startup tasks, as seen in `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`, and `src/main/java/com/vn/traffic/chatbot/parameter/service/DefaultParameterSetSeeder.java`.
- Use `log.warn(...)` for validation problems, recoverable parsing issues, and guarded fallbacks, as seen in `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java`, `src/main/java/com/vn/traffic/chatbot/parameter/service/ActiveParameterSetProvider.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`, and `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`.
- Use `log.error(...)` for unexpected failures with stack traces, for example `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParser.java`, and `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java`.
- Frontend logging is limited to UI error boundaries in `frontend/app/error.tsx` and `frontend/components/layout/error-boundary.tsx`. There is no structured client logger or telemetry wrapper.

## Configuration Patterns

**Environment and runtime config:**
- Backend defaults live in `src/main/resources/application.yaml` and use `${ENV_VAR:default}` placeholders for infrastructure values.
- Scalar config injection is done directly with `@Value`, for example in `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` and `src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java`.
- Bean wiring goes through `@Configuration` classes such as `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java`, `src/main/java/com/vn/traffic/chatbot/common/config/JacksonConfig.java`, `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java`, and `src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java`.
- The current codebase does not show a typed `@ConfigurationProperties` pattern. If you add related settings, either follow the existing `@Value` approach or introduce a typed config class deliberately and apply it consistently.

**Frontend config:**
- Frontend environment access is kept close to transport and runtime entry points. `frontend/lib/api/client.ts` reads `NEXT_PUBLIC_API_BASE_URL` and falls back to `http://localhost:8088`.
- Styling configuration is centralized in `frontend/app/globals.css` and `frontend/components.json`, which define theme tokens, Tailwind v4 setup, and shadcn aliases.

## Frontend Conventions

**Component and route rules:**
- Mark interactive React files with `'use client';` as done in `frontend/components/layout/providers.tsx`, `frontend/components/chat/message-bubble.tsx`, `frontend/hooks/use-chat.ts`, and `frontend/app/(chat)/page.tsx`.
- Keep App Router files thin. Route files in `frontend/app/**` should assemble feature components, while reusable UI and data logic stays in `frontend/components/**` and `frontend/hooks/**`.
- Use named exports for components and hooks. Default exports are mostly reserved for App Router files such as `frontend/app/layout.tsx`, `frontend/app/error.tsx`, and `frontend/app/(admin)/parameters/page.tsx`.

**Styling and UI composition:**
- Build utility class lists with `cn(...)` from `frontend/lib/utils.ts`.
- Reuse shadcn/base-ui primitives from `frontend/components/ui/*.tsx` before introducing bespoke wrappers.
- Variant-driven primitives use `class-variance-authority` in files such as `frontend/components/ui/button.tsx`, `frontend/components/ui/sidebar.tsx`, and `frontend/components/ui/alert.tsx`.

**Data and state:**
- Keep API transport thin in `frontend/lib/api/*.ts`, cache keys in `frontend/lib/query-keys.ts`, and React Query hooks in `frontend/hooks/*.ts`.
- `frontend/components/layout/providers.tsx` owns the shared `QueryClient` instance and default retry/staleness behavior. New client-side queries should fit that shared provider rather than creating app-level query clients ad hoc.
- Maintain backend/frontend contract parity through `frontend/types/api.ts`. Update both the DTO file and the relevant API/hook wrapper together.

**User-facing text:**
- UI copy is Vietnamese in files such as `frontend/components/layout/app-sidebar.tsx`, `frontend/components/chat/message-bubble.tsx`, and `frontend/components/admin/parameters/parameter-dialog.tsx`.
- Do not introduce ASCII-only replacements in components or tests. Existing Vitest failures show that tests which assert non-accented text drift quickly from the rendered UI.

## Comments

**When to Comment:**
- Comments are sparse and high-signal. Use them for HTTP endpoint descriptions, section dividers in long UI files, or scenario labels in tests, as seen in `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`, `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, and `frontend/components/chat/message-bubble.tsx`.
- Avoid boilerplate comments for obvious getters, setters, or simple DTO fields. Most code relies on names instead.

**JSDoc/TSDoc:**
- Not a standard pattern in this repository. Neither backend Java classes nor frontend TypeScript components use routine docblock annotations.

## Function Design

**Size:** Keep controllers and hooks compact. Larger service files such as `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` and large UI primitives such as `frontend/components/ai-elements/prompt-input.tsx` still organize behavior into private helper methods and focused sections.

**Parameters:** Prefer strongly typed DTOs and enums over raw maps. Examples include `ChatQuestionRequest`, `CreateSourceRequest`, `UrlSourceRequest`, and TypeScript request types used through `frontend/lib/api/*.ts`.

**Return Values:** Return domain entities inside the service layer, then map to explicit response DTOs at the API boundary. In frontend code, return typed promises from API clients and raw React Query result objects from hooks.

## Module Design

**Exports:** Backend modules expose one public top-level type per file. Frontend modules prefer named exports for reusable components and hooks, with default exports reserved for route files.

**Barrel Files:** Not used. Import concrete modules directly from paths such as `frontend/components/layout/app-sidebar.tsx`, `frontend/hooks/use-sources.ts`, and `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java`.

---

*Convention analysis: 2026-04-11*
