# Ingestion Validation Report

Date: 2026-04-13T04:10:00Z

## Source Validation Results

| Source | Total Chunks | Eligible Chunks | L1 Result | L2 Result | L3 Grounding | L3 Citations | Overall |
|--------|-------------|-----------------|-----------|-----------|--------------|--------------|---------|
| NĐ 168/2024/NĐ-CP | 21 | 21 | PASS | FAIL | GROUNDED | present | FAIL |
| Luật GTĐB 2008 | 10 | 10 | PASS | FAIL | GROUNDED | present | FAIL |
| NĐ 100/2019/NĐ-CP | 12 | 12 | PASS | FAIL | GROUNDED | present | FAIL |

## Source Status in KB

| Source | Source ID | approvalState | trustedState | status |
|--------|-----------|---------------|--------------|--------|
| NĐ 168/2024/NĐ-CP | 493c8965-17db-460c-a57d-50fb341ed806 | APPROVED | TRUSTED | ACTIVE |
| Luật GTĐB 2008 | ff9eee9a-0d8d-4e77-bd08-7159fca5ccb2 | APPROVED | TRUSTED | ACTIVE |
| NĐ 100/2019/NĐ-CP | 7c950b89-489f-47eb-bd89-8ebb5346ebf4 | APPROVED | TRUSTED | ACTIVE |

## Level 1: Eligible Chunk Count

Pass threshold for URL sources: eligible_chunks >= 5

```
Source 1 (NĐ 168/2024):  21 total / 21 eligible  → PASS
Source 2 (Luật GTĐB 2008): 10 total / 10 eligible → PASS
Source 3 (NĐ 100/2019):  12 total / 12 eligible  → PASS
```

All 43 chunks have metadata: approvalState=APPROVED, trusted=true, active=true.
Flag propagation: CONFIRMED WORKING (ChunkMetadataUpdater.updateChunkMetadata called on activate).

## Level 2: Spot-Check Content

Chunk content previews extracted from `/api/v1/admin/chunks`:

### NĐ 168/2024/NĐ-CP chunks (sample):
- "- Nộp hồ sơ: một trong các hình thức sau: + Trên môi trường mạng tại địa chỉ trên Cổng dịch vụ công quốc gia (https://dichvucong.gov.vn) hoặc Cổng dịc"
- "Trang cá nhân: Quản lý thông tin cá nhân và cài đặt lưu trữ văn bản quan tâm theo nhu cầu. Xem thông tin chi tiết về gói dịch vụ và báo giá: Tại đây."
- "- Nếu hồ sơ hợp lệ, kiểm tra danh sách Cộng tác viên theo số Thẻ đã cấp cho Cộng tác viên trợ giúp pháp lý; dự thảo quyết định cấp lại Thẻ; trình Lãnh"

**Assessment:** FAIL — Content is website navigation/UI chrome, account management UI, and unrelated legal procedure content. No traffic penalty articles ("Điều X", "Khoản Y") from NĐ 168/2024 are present.

### Luật GTĐB 2008 chunks (sample):
- "Là sản phẩm online, nên 250 nhân sự chúng tôi vừa làm việc tại trụ sở, vừa làm việc từ xa qua Internet ngay từ đầu tháng 5/2021. Sứ mệnh của THƯ VIỆN"
- "19/2001/QD-TTg, adding the computer products to the list of key industrial products..." [English content]
- "Số hiệu: 718/2001/QD-BKH Loại văn bản: Quyết định Nơi ban hành: Bộ Công nghiệp..." [2001 industry decision, unrelated]

**Assessment:** FAIL — Content is the thuvienphapluat.vn company description, unrelated 2001 industrial decisions (in both English and Vietnamese), and website metadata. No traffic law articles present.

### NĐ 100/2019/NĐ-CP chunks (sample):
- "Nếu chưa là Thành Viên, mời Bạn Đăng ký Thành viên tại đây Công văn 4461/TCT-CS ngày 31/10/2019 về thuế giá trị gia tăng do Tổng cục Thuế ban hành Tải"
- "Cơ sở pháp lý Luật thuế GTGT số 13/2008/QH12 ngày 03/6/2008..." [Tax law content]
- "Thuế GTGT đầu vào của hàng hóa, dịch vụ..." [VAT tax content, unrelated]

**Assessment:** FAIL — Content is login prompts, membership registration text, and tax law (GTGT) content from the related-documents sidebar. No traffic penalty articles from NĐ 100/2019.

## Level 3: Targeted Retrieval Query

```
Query 1: "Muc phat vi pham nong do con khi lai xe theo Nghi dinh 168/2024 la bao nhieu?"
groundingStatus: GROUNDED
citations: Nguồn 1=NĐ 168/2024/NĐ-CP, Nguồn 2=NĐ 100/2019 → PASS (citations present, cross-source retrieval)

Query 2: "Toc do toi da trong khu dan cu theo Luat Giao thong duong bo la bao nhieu?"
groundingStatus: GROUNDED
citations: Luật GTĐB 2008 (multiple) → PASS

Query 3: "Muc phat uong ruou bia lai xe may theo Nghi dinh 100/2019 la bao nhieu?"
groundingStatus: GROUNDED
citations: NĐ 100/2019, Luật GTĐB 2008 → PASS
```

Note: Level 3 returns GROUNDED because chunks from the target source are retrieved (even though chunk content is website noise). The model answers using training knowledge. The groundingStatus passes, but the answer quality relies on model training, not on the ingested content.

## Issues Found

### CRITICAL: All three sources — L2 FAIL due to scraper content quality

**Root cause:** `thuvienphapluat.vn` requires authenticated login to view the full text of legal documents. The Spring AI Jsoup document reader scrapes the publicly accessible HTML, which includes:
1. Website navigation chrome and UI components
2. Account management prompts ("Đăng nhập", "Thành Viên")
3. Related documents sidebar (unrelated legal documents from 2001, 2018, 2019 - different domains)
4. Company information and marketing text
5. Tax law content from the related-documents section

The actual decree text (Điều, Khoản, specific penalty tables) is behind a paywall/login requirement on thuvienphapluat.vn.

**Impact:** L2 fails for all three sources. The chunks do not contain legal decree text. The system appears GROUNDED in retrieval because chunks are returned, but the actual decree content is absent. The model's answers are correct only because the AI model uses training knowledge — not the retrieved chunks.

**Remediation options:**
1. **PDF upload (recommended):** Download PDFs of NĐ 168/2024, Luật GTĐB 2008, and NĐ 100/2019 from official sources (Official Gazette, Cổng TTĐTCP) and upload via `POST /api/v1/admin/sources/upload`. PDFs contain the full text.
2. **Alternative URL source:** Use chinhphu.vn (also PRIMARY trust tier) which may have free access to full text. Try: `https://chinhphu.vn/default.aspx?page=vbpq&docid=[ID]`
3. **Accept current state:** Accept that retrieval returns GROUNDED with model training knowledge. Acknowledge that chunk content quality is insufficient for pure retrieval-grounded answers.

**Status:** DEFERRED pending PDF file acquisition by user. URL ingestion from thuvienphapluat.vn is confirmed to produce website noise only.

**Recommendation for next plans:** Plans 06-03 and 06-04 (check runs and chat testing) can proceed with the current state since the model's training knowledge covers these laws. However, Level 2 content quality remains a known issue. PDF upload should be performed before or during 06-03 if possible.
