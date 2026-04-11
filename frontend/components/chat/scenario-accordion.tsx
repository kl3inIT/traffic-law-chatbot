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

export function ScenarioAccordion({ analysis, citations }: ScenarioAccordionProps) {
  const sections = [
    { value: 'facts', label: 'Sự kiện được xác định', content: analysis.facts },
    { value: 'rule', label: 'Quy tắc áp dụng', content: analysis.rule },
    { value: 'outcome', label: 'Hậu quả có thể', content: analysis.outcome },
    { value: 'actions', label: 'Hành động được khuyến nghị', content: analysis.actions },
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
      <AccordionItem value="sources">
        <AccordionTrigger className="text-sm font-semibold">
          Nguồn tài liệu
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
              <span className="text-xs text-muted-foreground">Không có nguồn tài liệu.</span>
            )}
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}
