'use client';

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import type { ScenarioAnalysisResponse } from '@/types/api';

interface ScenarioAccordionProps {
  analysis: ScenarioAnalysisResponse;
  citations?: { inlineLabel: string; sourceTitle: string }[];
}

// Per UI-SPEC: 5 sections with Vietnamese labels
// "Nguon tai lieu" section is always expanded by default
export function ScenarioAccordion({ analysis, citations }: ScenarioAccordionProps) {
  const sections = [
    { value: 'facts', label: 'Su kien duoc xac dinh', content: analysis.facts },
    { value: 'rule', label: 'Quy tac ap dung', content: analysis.rule },
    { value: 'outcome', label: 'Hau qua co the', content: analysis.outcome },
    { value: 'actions', label: 'Hanh dong duoc khuyen nghi', content: analysis.actions },
  ];

  return (
    <Accordion multiple defaultValue={['sources']}>
      {sections.map((section) => (
        <AccordionItem key={section.value} value={section.value}>
          <AccordionTrigger className="text-sm font-semibold">
            {section.label}
          </AccordionTrigger>
          <AccordionContent>
            {Array.isArray(section.content) ? (
              <ul className="list-disc pl-4 text-sm space-y-1">
                {section.content.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            ) : (
              <p className="text-sm">{section.content}</p>
            )}
          </AccordionContent>
        </AccordionItem>
      ))}
      {/* Sources section -- always expanded by default */}
      <AccordionItem value="sources">
        <AccordionTrigger className="text-sm font-semibold">
          Nguon tai lieu
        </AccordionTrigger>
        <AccordionContent>
          <div className="flex flex-wrap gap-1">
            {analysis.sources?.map((src, i) => (
              <Badge key={i} variant="secondary">
                {src.title}
              </Badge>
            ))}
            {citations?.map((cit, i) => (
              <Badge key={`cit-${i}`} variant="outline">
                [{cit.inlineLabel}] {cit.sourceTitle}
              </Badge>
            ))}
            {(!analysis.sources?.length && !citations?.length) && (
              <span className="text-xs text-muted-foreground">Khong co nguon tai lieu.</span>
            )}
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}
