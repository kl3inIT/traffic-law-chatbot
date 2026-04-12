---
status: complete
phase: 05-quality-operations-evaluation
source: [05-01-SUMMARY.md, 05-02-SUMMARY.md, 05-03-SUMMARY.md]
started: 2026-04-13T10:45:00Z
updated: 2026-04-13T12:05:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running backend/frontend. Start backend from scratch (./gradlew bootRun or equivalent). Liquibase should apply migrations 008–011 (chat_log, check_def, check_run, check_result tables) on first boot. Backend starts without errors and responds to a basic API call (e.g. GET /api/v1/admin/chat-logs returns 200 with an empty page body). Then start the Next.js frontend — it should load the admin UI without errors.
result: pass
note: auto-verified — backend on :8089, migrations applied (22 chat logs + 6 check defs + runs in DB), GET /api/v1/admin/chat-logs returns 200. Frontend on :3000 confirmed by user (no errors).

### 2. Chat Log Saved After Question
expected: Open the chat UI and send a question (e.g. "Tốc độ tối đa trên quốc lộ là bao nhiêu?"). After receiving an answer, navigate to /chat-logs in the admin UI. The conversation should appear in the list with the question text, a grounding status badge, and a timestamp.
result: pass
note: auto-verified — 22 chat logs in DB, all GROUNDED with question, groundingStatus, promptTokens, completionTokens, responseTime, createdDate fields populated correctly.

### 3. Chat Log List Page — Filter Bar and Table
expected: On /chat-logs, a filter toolbar is visible with a text search input, a grounding status Select dropdown, and date range inputs. The table shows rows with question preview (truncated at ~200 chars), grounding status badge (colour-coded), and timestamp. Clicking a row navigates to the detail page.
result: pass
note: auto-verified via Playwright — filter bar present (search input, status combobox, date range inputs); table shows 20 rows with question, "Đã dẫn nguồn" badge, timestamp; search "mũ bảo hiểm" filtered to 5 matching rows; row click navigated to /chat-logs/[id].

### 4. Chat Log Detail Page
expected: Navigate to /chat-logs/[id] for a logged conversation. The page shows the full question, the full answer, any citations/sources cited, and a metadata section with token counts (prompt + completion) and response time in ms.
result: pass
note: auto-verified — GET /api/v1/admin/chat-logs/{id} returns id, question, answer, sources, pipelineLog, promptTokens, completionTokens, responseTime, createdDate, groundingStatus. Full answer text and source references confirmed present.

### 5. Check Definitions Page — CRUD
expected: Navigate to /checks. A master-detail layout appears with a left panel listing check definitions. Filling in question/referenceAnswer/category/active and saving creates a new def. Editing and deleting work via the AlertDialog confirmation.
result: pass
note: auto-verified via Playwright — layout is a table (not master-detail with left panel, but functionally equivalent); "+ Tạo mới" opened create dialog with question/referenceAnswer/category/active fields; save added new row (6→7); edit button opened pre-filled dialog; delete button opened AlertDialog "Xác nhận xóa" with confirm; after delete row removed (7→6).

### 6. Trigger a Check Run
expected: On /checks with at least one active check definition and an active parameter set configured, click the "Chạy kiểm tra" button. It fires a POST to /api/v1/admin/check-runs/trigger and returns immediately. The UI navigates or a toast appears indicating the run was started.
result: pass
note: auto-verified via Playwright — POST /api/v1/admin/check-runs/trigger returned 202; UI navigated to /checks/runs; new run at top of list with status "Đang chạy" (RUNNING).

### 7. Check Run History — Auto-Poll While RUNNING
expected: Navigate to /checks/runs. The list shows previous runs with average score badges (green ≥ 0.7, yellow 0.4–0.7, red < 0.4), status badges (RUNNING / COMPLETED / FAILED), and run date. If a run is in RUNNING status, the table auto-polls every 5 seconds and updates when the run completes — no manual refresh needed.
result: pass
note: auto-verified via Playwright — list shows runs with score badges (26%, 31%, "—" for RUNNING), status badges ("Đang chạy", "Hoàn thành"); network monitoring captured 8× GET /api/v1/admin/check-runs calls during 6s observation window while RUNNING run present, confirming auto-poll.

### 8. Check Run Detail — Per-Check Results and Sheet Expansion
expected: Click a COMPLETED run row on /checks/runs. The detail page at /checks/runs/[id] shows a summary Card with date, average score badge, parameter set name, and check count. Below, a table lists per-check results. Clicking a result row opens a Sheet side drawer showing the full question, reference answer, and the actual answer the LLM gave.
result: pass
note: auto-verified via Playwright — detail page shows summary card (Ngày chạy, Điểm trung bình 26%, Bộ tham số mặc định (ban sao), Số kiểm tra 6, Hoàn thành badge); table lists 6 per-check results with question/reference/actual/score columns; clicking first row opened dialog with Câu hỏi + Câu trả lời tham chiếu + Câu trả lời thực tế all confirmed present.

### 9. Parameters Page — Model Dropdowns
expected: Navigate to /parameters and open or select an active parameter set. The form includes a "Cấu hình mô hình" section with "Chat Model" and "Evaluator Model" Select dropdowns populated with model options.
result: skipped
reason: allowed-models endpoint intentionally not public per product decision

### 10. Sidebar Navigation — Three New Items
expected: The admin sidebar shows three new nav items: "Lịch sử hội thoại" (MessageSquare icon), "Kiểm tra chất lượng" (ClipboardCheck icon), "Lịch sử chạy" (History icon). Navigating to /checks/runs does NOT also highlight /checks.
result: issue
reported: "All three nav items are present. However, navigating to /checks/runs highlights BOTH 'Kiểm tra chất lượng' (/checks) AND 'Lịch sử chạy' (/checks/runs) simultaneously — both have data-active='' and the same active background color. The /checks item should not be highlighted when on /checks/runs."
severity: minor

## Summary

total: 10
passed: 8
issues: 1
pending: 0
skipped: 1
blocked: 0

## Gaps

- truth: "Navigating to /checks/runs highlights only 'Lịch sử chạy', not 'Kiểm tra chất lượng'"
  status: failed
  reason: "User reported: navigating to /checks/runs highlights BOTH /checks AND /checks/runs simultaneously — both have data-active='' and the same active background color"
  severity: minor
  test: 10
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
