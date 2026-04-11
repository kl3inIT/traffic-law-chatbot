---
status: partial
phase: 04-next-js-chat-admin-app
source: [04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md, 04-05-SUMMARY.md, 04-06-SUMMARY.md, 04-07-SUMMARY.md]
started: 2026-04-11T00:00:00Z
updated: 2026-04-11T12:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Next.js frontend application serves correctly
expected: User navigates to localhost:3000 and sees the chat page with sidebar layout. No TypeScript or runtime errors visible in browser console.
result: pass

### 2. Navigation Sidebar
expected: User sees a persistent sidebar with 4 Vietnamese navigation items — Trò chuyện, Quản lý nguồn, Chỉ mục kiến thức, Bộ tham số AI — each navigating to its respective section when clicked.
result: pass

### 3. Send and receive chat messages
expected: User types a message in the chat input, presses Enter, sees their message appear as a bubble, and receives an AI response bubble with formatted content. Messages are visually distinct (user vs assistant).
result: pass

### 4. Scenario Analysis Accordion
expected: When the AI returns a scenario analysis, the response shows an expandable accordion with Vietnamese section headers (Tình huống, Phân tích, Quyết định, Hành động, Cơ sở) that can be toggled individually.
result: issue
reported: "Accordion sections wrong — got Kết luận/Căn cứ pháp lý/Mức phạt/Giấy tờ instead of expected 5 sections. Raw internal data block (Sự kiện được xác định, Quy tắc áp dụng) visible in response body. Citations show truncated document chunk text instead of clean source names. Empty Hành động section."
severity: major

### 5. Chat History Rendering
expected: User opens a saved chat thread and sees the full conversation history with all previous user and AI messages rendered with the same bubble styling as new messages, including structured accordion format for structured responses.
result: pass

### 6. Source Management DataTable
expected: Admin visits /sources and sees a table of sources with status and approval state badges. Selecting a source row enables the bulk action toolbar buttons accordingly.
result: issue
reported: "Tích vào row nhưng không ấn được action — bulk action buttons vẫn disabled sau khi select row"
severity: major

### 7. Bulk Source Actions
expected: Admin selects multiple source rows via checkboxes; the toolbar shows enabled Phê duyệt / Kích hoạt / Từ chối buttons (each showing count of eligible rows). Clicking executes the action on all selected eligible rows.
result: blocked
blocked_by: prior-phase
reason: "Row selection broken (test 6) — cannot test bulk actions until selection is fixed"

### 8. Source Reingest Action
expected: Admin opens the ... dropdown on an approved/active/rejected source and clicks Nhập lại. A confirmation dialog appears. After confirming, the source resets to DRAFT/PENDING status.
result: pass

### 9. Source Pagination
expected: When more than 20 sources exist, pagination controls appear showing current page / total pages and total count. Trước/Sau buttons navigate between pages; row selection resets on page change.
result: issue
reported: "Nút Trước/Sau không ấn được. Console error: <button> cannot be a descendant of <button> — PopoverTrigger asChild={true} wrapping shadcn Button both render <button>, causing hydration error in IndexChunkTable"
severity: major

### 10. Knowledge Index Cards
expected: Admin visits /index and sees two cards showing index readiness metrics (approved sources, trusted chunks, active chunks, total chunks) with a refresh button that reloads the numbers.
result: pass

### 11. Index Chunk Combobox Filter
expected: Admin uses the searchable combobox on the index page to filter chunks by source. Typing in the combobox narrows the source list; selecting one filters the chunk table to show only that source's chunks.
result: pass

### 12. Create Parameter Set
expected: Admin visits /parameters, clicks Tạo bộ tham số, enters a name and YAML content in the monospace editor, submits, and the new set appears in the table with inactive status.
result: pass

### 13. Activate Parameter Set
expected: Admin opens the ... menu on an inactive parameter set and clicks Kích hoạt. The selected set is now marked active (Đang hoạt động badge) and the previously active set loses the badge.
result: pass

### 14. Copy Parameter Set
expected: Admin opens the ... menu on any parameter set and clicks Sao chép. A copy appears in the list with (bản sao) appended to the name, in inactive state.
result: pass

### 15. Delete Guard on Active Parameter Set
expected: Admin opens the ... menu on the currently active parameter set and sees that the Xóa option is absent (hidden). Attempting to delete via any means is blocked.
result: pass

## Summary

total: 15
passed: 10
issues: 3
skipped: 0
blocked: 1
pending: 0

## Gaps

- truth: "AI response shows accordion with sections Tình huống, Phân tích, Quyết định, Hành động, Cơ sở; citations show clean source names not truncated chunk text; no raw internal data (Sự kiện được xác định) visible"
  status: failed
  reason: "User reported: Accordion sections wrong — got Kết luận/Căn cứ pháp lý/Mức phạt/Giấy tờ instead of expected 5 sections. Raw internal data block (Sự kiện được xác định, Quy tắc áp dụng) visible in response body. Citations show truncated document chunk text instead of clean source names. Empty Hành động section."
  severity: major
  test: 4
  artifacts: []
  missing: []

- truth: "Selecting a source row via checkbox enables bulk action toolbar buttons (Phê duyệt/Kích hoạt/Từ chối) for eligible rows"
  status: failed
  reason: "User reported: Tích vào row nhưng không ấn được action — bulk action buttons vẫn disabled sau khi select row"
  severity: major
  test: 6
  artifacts: []
  missing: []

- truth: "Pagination Trước/Sau buttons navigate between pages; no hydration errors in console"
  status: failed
  reason: "User reported: Nút Trước/Sau không ấn được. Console error: <button> cannot be a descendant of <button> — PopoverTrigger asChild={true} wrapping shadcn Button both render <button>, causing hydration error in IndexChunkTable"
  severity: major
  test: 9
  artifacts: [frontend/components/admin/index/index-chunk-table.tsx]
  missing: []
