'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Message, MessageContent, MessageToolbar } from '@/components/ai-elements/message';
import { ScenarioAccordion } from './scenario-accordion';
import type { ChatAnswerResponse, CitationResponse } from '@/types/api';

// ─── User bubble ─────────────────────────────────────────────────────────────

export function UserBubble({ content }: { content: string }) {
  return (
    <Message from="user">
      <MessageContent>
        <p className="text-sm whitespace-pre-wrap">{content}</p>
      </MessageContent>
    </Message>
  );
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function Section({ title, items }: { title: string; items: string[] }) {
  if (!items.length) return null;
  return (
    <div className="space-y-1">
      <p className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">{title}</p>
      <ul className="space-y-0.5 text-sm">
        {items.map((item, i) => (
          <li key={i} className="flex gap-2">
            <span className="text-muted-foreground mt-0.5 shrink-0">•</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function CitationList({ citations }: { citations: CitationResponse[] }) {
  if (!citations.length) return null;
  return (
    <div className="space-y-2">
      <p className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
        Nguồn tham khảo
      </p>
      <ol className="space-y-2">
        {citations.map((cit, i) => (
          <li key={i} className="bg-muted/40 rounded-md border p-2 text-xs">
            <div className="flex items-start gap-2">
              <span className="bg-primary/10 text-primary shrink-0 rounded px-1.5 py-0.5 text-[10px] font-bold">
                {cit.inlineLabel}
              </span>
              <div className="min-w-0 flex-1 space-y-0.5">
                <p className="leading-snug font-medium">{cit.sourceTitle}</p>
                {(cit.sectionRef || cit.pageNumber) && (
                  <p className="text-muted-foreground">
                    {cit.sectionRef}
                    {cit.sectionRef && cit.pageNumber ? ' · ' : ''}
                    {cit.pageNumber ? `Trang ${cit.pageNumber}` : ''}
                  </p>
                )}
              </div>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}

// ─── AI bubble ───────────────────────────────────────────────────────────────

export function AiBubble({ response }: { response: ChatAnswerResponse }) {
  const isScenario =
    response.responseMode === 'SCENARIO_ANALYSIS' || response.responseMode === 'FINAL_ANALYSIS';

  // Use structured fields if available, else fall back to answer blob
  const hasStructured =
    response.legalBasis.length > 0 ||
    response.penalties.length > 0 ||
    response.requiredDocuments.length > 0 ||
    response.procedureSteps.length > 0 ||
    response.nextSteps.length > 0;

  return (
    <Message from="assistant">
      <MessageContent className="w-full max-w-[85%] space-y-3">
        {/* ── Kết luận ── */}
        {response.conclusion && (
          <div className="border-primary bg-primary/5 rounded-md border-l-4 px-3 py-2">
            <p className="text-primary text-xs font-semibold tracking-wide uppercase">Kết luận</p>
            <p className="mt-0.5 text-sm font-medium">{response.conclusion}</p>
          </div>
        )}

        {/* ── Scenario analysis ── */}
        {isScenario && response.scenarioAnalysis ? (
          <ScenarioAccordion analysis={response.scenarioAnalysis} citations={response.citations} />
        ) : hasStructured ? (
          /* ── Structured standard response ── */
          <div className="space-y-3">
            <Section title="Căn cứ pháp lý" items={response.legalBasis} />
            <Section title="Mức phạt / Hậu quả" items={response.penalties} />
            <Section title="Giấy tờ cần thiết" items={response.requiredDocuments} />
            <Section title="Quy trình thực hiện" items={response.procedureSteps} />
            <Section title="Bước tiếp theo" items={response.nextSteps} />
          </div>
        ) : (
          /* ── Fallback: raw answer blob ── */
          response.answer && <p className="text-sm whitespace-pre-wrap">{response.answer}</p>
        )}

        {/* ── Nguồn tham khảo ── */}
        {response.citations.length > 0 && (!isScenario || !response.scenarioAnalysis) && (
          <>
            <Separator />
            <CitationList citations={response.citations} />
          </>
        )}
      </MessageContent>

      {/* ── Disclaimer / uncertainty ── */}
      {(response.disclaimer || response.uncertaintyNotice) && (
        <MessageToolbar className="mt-0 justify-start">
          <div className="flex flex-col gap-1">
            {response.disclaimer && (
              <p className="text-muted-foreground text-xs">{response.disclaimer}</p>
            )}
            {response.uncertaintyNotice && response.uncertaintyNotice !== response.disclaimer && (
              <p className="text-muted-foreground text-xs">{response.uncertaintyNotice}</p>
            )}
          </div>
        </MessageToolbar>
      )}
    </Message>
  );
}

// ─── Loading skeleton ─────────────────────────────────────────────────────────

export function AiBubbleLoading() {
  return (
    <Message from="assistant">
      <MessageContent className="w-64 space-y-2">
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
      </MessageContent>
    </Message>
  );
}
