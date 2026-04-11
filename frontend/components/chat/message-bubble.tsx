'use client';

import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { ScenarioAccordion } from './scenario-accordion';
import type { ChatAnswerResponse } from '@/types/api';

// User message bubble
export function UserBubble({ content }: { content: string }) {
  return (
    <div className="flex justify-end gap-2">
      <div className="bg-primary text-primary-foreground rounded-lg p-4 max-w-[70%]">
        <p className="text-sm whitespace-pre-wrap">{content}</p>
      </div>
      <Avatar className="h-8 w-8">
        <AvatarFallback>U</AvatarFallback>
      </Avatar>
    </div>
  );
}

// AI message bubble -- branches on responseMode per Pattern 5 in RESEARCH.md
export function AiBubble({ response }: { response: ChatAnswerResponse }) {
  const isScenario =
    response.responseMode === 'SCENARIO_ANALYSIS' ||
    response.responseMode === 'FINAL_ANALYSIS';

  return (
    <div className="flex gap-2">
      <Avatar className="h-8 w-8">
        <AvatarFallback>AI</AvatarFallback>
      </Avatar>
      <div className="bg-card border rounded-lg p-4 max-w-[85%]">
        {/* Conclusion first if present */}
        {response.conclusion && (
          <p className="text-sm font-semibold mb-2">{response.conclusion}</p>
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
              <p className="text-sm whitespace-pre-wrap">{response.answer}</p>
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
              <div className="mt-2 flex flex-wrap gap-1">
                {response.citations.map((cit, i) => (
                  <Badge key={i} variant="outline" className="text-xs">
                    [{cit.inlineLabel}] {cit.sourceTitle}
                  </Badge>
                ))}
              </div>
            )}
          </>
        )}

        {/* Disclaimer -- always at bottom in muted style per UI-SPEC */}
        {response.disclaimer && (
          <p className="text-muted-foreground text-xs mt-2">
            {response.disclaimer}
          </p>
        )}

        {/* Uncertainty notice */}
        {response.uncertaintyNotice && (
          <p className="text-muted-foreground text-xs mt-1">
            {response.uncertaintyNotice}
          </p>
        )}
      </div>
    </div>
  );
}

// Loading state -- 3 skeleton lines per UI-SPEC
export function AiBubbleLoading() {
  return (
    <div className="flex gap-2">
      <Avatar className="h-8 w-8">
        <AvatarFallback>AI</AvatarFallback>
      </Avatar>
      <div className="bg-card border rounded-lg p-4 max-w-[85%] space-y-2">
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
      </div>
    </div>
  );
}
