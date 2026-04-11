'use client';

import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Message,
  MessageContent,
  MessageResponse,
  MessageToolbar,
} from '@/components/ai-elements/message';
import { ScenarioAccordion } from './scenario-accordion';
import type { ChatAnswerResponse } from '@/types/api';

// User message bubble
export function UserBubble({ content }: { content: string }) {
  return (
    <Message from="user">
      <MessageContent>
        <MessageResponse>{content}</MessageResponse>
      </MessageContent>
    </Message>
  );
}

// AI message bubble -- branches on responseMode per Pattern 5 in RESEARCH.md
export function AiBubble({ response }: { response: ChatAnswerResponse }) {
  const isScenario =
    response.responseMode === 'SCENARIO_ANALYSIS' ||
    response.responseMode === 'FINAL_ANALYSIS';

  return (
    <Message from="assistant">
      <MessageContent>
        {/* Conclusion first if present */}
        {response.conclusion && (
          <p className="text-sm font-semibold">{response.conclusion}</p>
        )}

        {/* Main content branching */}
        {isScenario && response.scenarioAnalysis ? (
          <ScenarioAccordion
            analysis={response.scenarioAnalysis}
            citations={response.citations}
          />
        ) : (
          <>
            {response.answer && (
              <MessageResponse>{response.answer}</MessageResponse>
            )}

            {/* Pending facts for CLARIFICATION_NEEDED */}
            {response.responseMode === 'CLARIFICATION_NEEDED' &&
              response.pendingFacts.length > 0 && (
                <div className="mt-2 space-y-1">
                  {response.pendingFacts.map((fact, i) => (
                    <p key={fact.factKey ?? i} className="text-sm">
                      - {fact.question}
                    </p>
                  ))}
                </div>
              )}

            {/* Citations as badges */}
            {response.citations.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {response.citations.map((cit, i) => (
                  <Badge key={i} variant="outline" className="text-xs">
                    [{cit.inlineLabel}] {cit.sourceTitle}
                  </Badge>
                ))}
              </div>
            )}
          </>
        )}
      </MessageContent>

      {/* Disclaimer / uncertainty in toolbar area */}
      {(response.disclaimer || response.uncertaintyNotice) && (
        <MessageToolbar className="mt-0 justify-start">
          <div className="flex flex-col gap-1">
            {response.disclaimer && (
              <p className="text-muted-foreground text-xs">{response.disclaimer}</p>
            )}
            {response.uncertaintyNotice && (
              <p className="text-muted-foreground text-xs">{response.uncertaintyNotice}</p>
            )}
          </div>
        </MessageToolbar>
      )}
    </Message>
  );
}

// Loading state -- 3 skeleton lines per UI-SPEC
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
