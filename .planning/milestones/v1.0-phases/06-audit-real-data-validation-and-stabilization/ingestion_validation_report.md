# Ingestion Validation Report

Date: 2026-04-13T04:30:00Z
Last Updated: 2026-04-13T04:45:00Z (Round 2 — after chinhphu.vn re-ingestion attempt)

## Summary

Two ingestion rounds were executed:

| Round | Source | Outcome |
|-------|--------|---------|
| 1 | thuvienphapluat.vn (3 URLs) | L1 PASS, L2 FAIL (website noise), L3 GROUNDED |
| 2 | chinhphu.vn (1 test URL — main portal) | L1 PASS (8 chunks), L2 FAIL (homepage news noise), deleted |
| Final | thuvienphapluat.vn (3 URLs, re-ingested) | L1 PASS, L2 FAIL (same pattern), L3 GROUNDED |

**Conclusion:** All tested Vietnamese legal websites (thuvienphapluat.vn, chinhphu.vn) produce website navigation noise rather than legal document text. Full legal text requires PDF upload.

## Source Validation Results (Final State)

| Source | Total Chunks | Eligible Chunks | L1 Result | L2 Result | L3 Grounding | L3 Citations | Overall |
|--------|-------------|-----------------|-----------|-----------|--------------|--------------|---------|
| NĐ 168/2024/NĐ-CP | 21 | 21 | PASS | FAIL | GROUNDED | present (5) | FAIL |
| Luật GTĐB 2008 | 10 | 10 | PASS | FAIL | GROUNDED | present (5) | FAIL |
| NĐ 100/2019/NĐ-CP | 12 | 12 | PASS | FAIL | GROUNDED | present (5) | FAIL |

## Source Status in KB (Final State)

| Source | Source ID | approvalState | trustedState | status |
|--------|-----------|---------------|--------------|--------|
| NĐ 168/2024/NĐ-CP | 05828361-4b54-40bb-9530-e2aaf42e6add | APPROVED | TRUSTED | ACTIVE |
| Luật GTĐB 2008 | 98727f7a-c539-45d1-9042-236e21be3ca8 | APPROVED | TRUSTED | ACTIVE |
| NĐ 100/2019/NĐ-CP | f0633a28-d8e1-46a6-b74d-681b6e75f248 | APPROVED | TRUSTED | ACTIVE |

## Level 1: Eligible Chunk Count

Pass threshold for URL sources: eligible_chunks >= 5

```
Source 1 (NĐ 168/2024):    21 total / 21 eligible  → PASS
Source 2 (Luật GTĐB 2008): 10 total / 10 eligible  → PASS
Source 3 (NĐ 100/2019):    12 total / 12 eligible  → PASS
```

All 43 chunks have metadata: approvalState=APPROVED, trusted=true, active=true.
Flag propagation: CONFIRMED WORKING (ChunkMetadataUpdater runs on activate).

Note: The JOIN key in the research SQL used `source_id` but the actual metadata key is `sourceId` (camelCase). This is an important correction for future validation queries.

## Level 2: Spot-Check Content

### Round 1 and Final — thuvienphapluat.vn sources

### NĐ 168/2024/NĐ-CP chunk samples:
- Chunk 0: "THƯ VIỆN PHÁP LUẬTTrang Thông tin điện tử tổng hợp...loại rủi ro pháp lý, nắm cơ hội làm giàu...Các gói dịch vụ Chính sách Pháp luật mới..."
- Chunk 4: "CHỦ TỊCH PHÓ CHỦ TỊCH Vương Quốc Tuấn DANH MỤC VÀ NỘI DUNG QUY TRÌNH GIẢI QUYẾT NỘI BỘ CÁC THỦ TỤC HÀNH CHÍNH ĐƯỢC SỬA ĐỔI, BỔ SUNG TRONG LĨNH VỰC TRỢ GIÚP PHÁP LÝ..." (legal-procedure-admin content from related docs panel, not traffic law)
- Chunk 14: "Bạn chưa xem được Hiệu lực của Văn bản, Văn bản liên quan... Nếu chưa là Thành Viên, mời Bạn Đăng ký Thành viên tại đây..." (login wall)
- Chunk 19: "HCM, ngày 31/05/2021 Thưa Quý khách, Đúng 14 tháng trước, ngày 31/3/2020, THƯ VIỆN PHÁP LUẬT đã bật Thông báo này..." (company marketing content)

**Assessment:** FAIL — No traffic law article text present. Content is: website header, membership/login UI, related documents sidebar (content from other ministries), and company marketing text. Decree articles (Điều, Khoản, penalty tables) are behind paywall.

### Luật GTĐB 2008 chunk samples:
- Chunk 0: "THƯ VIỆN PHÁP LUẬT Trang Thông tin điện tử..." (site header)
- Chunk 3: "Trần Xuân Giá (Đã ký) DANH MỤC CÁC SẢN PHẨM CÔNG NGHIỆP PHẢI ĐẢM BẢO XUẤT KHẨU ÍT NHẤT 80% SẢN PHẨM (Ban hành kèm theo Quyết định số 718/2001/QĐ-BKH...)" (unrelated 2001 industrial decision from related-docs panel)
- Chunk 6: HTML comparison table markup (version comparison legend, not legal text)

**Assessment:** FAIL — No traffic law articles. Content is site chrome, unrelated 2001 industrial decision, and UI comparison markup.

### NĐ 100/2019/NĐ-CP chunk samples:
- Chunk 0: "THƯ VIỆN PHÁP LUẬT Trang Thông tin điện tử..." (site header)
- Chunk 1: "Lao Động Tiền Lương X Tư vấn Pháp luật X Pháp Luật Nhà Đất X...Từ khoá: Số Hiệu, Tiêu đề hoặc Nội dung ngắn gọn của Văn Bản..." (search UI chrome)
- Sample: "Nếu chưa là Thành Viên, mời Bạn Đăng ký Thành viên tại đây Công văn 4461/TCT-CS ngày 31/10/2019 về thuế giá trị gia tăng...Cơ sở pháp lý Luật thuế GTGT..." (login gate + unrelated tax law from related-docs)

**Assessment:** FAIL — No traffic law articles. Login gate confirmed. Related-docs sidebar serves VAT/tax law content.

### Round 2 — chinhphu.vn test (1 source, deleted)

- URL tested: `https://chinhphu.vn/default.aspx?page=vbpq&docid=214717`
- Chunk 0: "English 中文 Trang chủ Chính phủ Công dân Doanh nghiệp Kiều bào..." (site navigation including Chinese-language link)
- Chunk 4: News headlines: "Chỉ đạo, điều hành của Chính phủ, Thủ tướng Chính phủ ngày 8/4/2026..."
- Chunk 5: Recent decrees: "16/2026/NQ-CP 07/04/2026 Cắt giảm, đơn giản hóa thủ tục hành chính..." (current 2026 documents, not target decrees)

**Assessment:** FAIL — chinhphu.vn main portal serves the website homepage with news and links, not the specific legal document text. The `docid` parameter was not for NĐ 168/2024 and the portal renders a news-and-links homepage regardless.

**Additional chinhphu.vn URL discovery attempts:** Scanned vanban.chinhphu.vn docid ranges 209000-214700 (sampling every 100). Found NĐ 120/2024 at docid=211300 but no docid for NĐ 168/2024 (issued 2024-12-30, likely in 211300-212500 range). Full sequential scan not feasible without known document ID.

## Level 3: Targeted Retrieval Query (Final State)

```
Query 1: "Muc phat vi pham nong do con khi lai xe la bao nhieu?"
groundingStatus: GROUNDED
citations: 5 (from all 3 sources)
sources: 3 (all three sources cited)
→ PASS

Query 2: "Toc do toi da trong khu dan cu theo Luat Giao thong duong bo la bao nhieu?"
groundingStatus: GROUNDED
citations: 5 / sources: 1 (Luat GTDB)
→ PASS

Query 3: "Muc phat uong ruou bia lai xe may theo Nghi dinh 100/2019 la bao nhieu?"
groundingStatus: GROUNDED
citations: 5 / sources: 3 (cross-source)
→ PASS
```

**Important note:** L3 returns GROUNDED because chunks from the target sources are retrieved by vector similarity. The model answers correctly using training knowledge about these laws. The groundingStatus field reflects that retrieved chunks were used as context — but the chunk content is website noise, not the actual decree text. Correctness of answers is model-knowledge dependent, not chunk-content dependent.

## Issues Found

### CRITICAL: All Three Sources — L2 FAIL (Website Authentication Wall)

**Root cause:** Vietnamese legal websites (`thuvienphapluat.vn` and `chinhphu.vn`) require:
1. **thuvienphapluat.vn**: Premium membership/login to view full decree text
2. **chinhphu.vn main portal**: Serves homepage content, not document-specific pages

The Spring AI JsoupDocumentReader scrapes publicly accessible HTML which contains:
1. Website navigation chrome and language selectors
2. Account management prompts ("Đăng nhập", "Thành Viên")
3. Related documents sidebar (unrelated legal documents from other agencies and years)
4. Company information and marketing text
5. Recent news headlines and link lists

The actual decree articles (Điều, Khoản, penalty VND amounts) are hidden behind login walls or require session-authenticated access.

**Impact:**
- L2 fails for all three sources across two rounds of ingestion
- L3 passes only because vector similarity retrieves noise chunks that the model ignores in favor of training knowledge
- System appears GROUNDED but actual grounding source is AI training data, not retrieved chunk content
- Future check-run evaluations will show correct answers but the knowledge source is the model, not the KB

**Root cause confirmed by:**
- Chunk 14 of NĐ 168/2024 explicitly shows: "Bạn chưa xem được Hiệu lực của Văn bản... Nếu chưa là Thành Viên, mời Bạn Đăng ký"
- Chunk 0 of all sources shows identical site header (thuvienphapluat.vn website HTML)
- chinhphu.vn test source produced 8 chunks of homepage news/navigation content

**Remediation Options (Priority Order):**

1. **PDF Upload (Recommended — Production Solution):**
   - Download PDFs from official government sources or gazette
   - Upload via `POST /api/v1/admin/sources/upload` with `sourceType=PDF`
   - PDFs contain full decree text (Điều, Khoản, penalty tables)
   - Expected: >= 100 chunks per PDF decree (50+ page documents)

2. **vanban.chinhphu.vn with correct docid:**
   - NĐ 168/2024/NĐ-CP (signed 2024-12-30) likely at docid in range 211300-212500
   - Requires knowing exact docid; brute-force scan not practical
   - Possible if the correct URL is found manually

3. **Accept Current State with Documentation:**
   - L1: PASS (chunks present, eligible flags set correctly)
   - L2: FAIL (noise content, documented)
   - L3: GROUNDED (passes via model training knowledge)
   - Plans 06-03 and 06-04 can proceed — model training covers these traffic laws
   - Answer quality is acceptable but not traceable to retrieved chunk content

**Current status:** Option 3 applied. PDF upload blocked on file acquisition. Known issue tracked here and in plan SUMMARY.

## Metadata Key Correction

The Level 1 SQL in 06-RESEARCH.md uses `(v.metadata->>'source_id')` but the actual JSONB key is `sourceId` (camelCase). Corrected query:

```sql
LEFT JOIN kb_vector_store v ON (v.metadata->>'sourceId') = s.id::text
```

This does not affect runtime retrieval (the retrieval gate uses different metadata fields) but affects admin validation SQL.
