import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { UserBubble, AiBubble, AiBubbleLoading } from '@/components/chat/message-bubble';
import type { ChatAnswerResponse } from '@/types/api';

describe('UserBubble', () => {
  it('renders user message content', () => {
    render(<UserBubble content="Xin chào" />);
    expect(screen.getByText('Xin chào')).toBeInTheDocument();
  });
});

describe('AiBubble', () => {
  const baseResponse: ChatAnswerResponse = {
    groundingStatus: 'GROUNDED',
    threadId: 'test-id',
    responseMode: 'STANDARD',
    answer: 'Câu trả lời từ AI',
    conclusion: null,
    disclaimer: 'Thông tin tham khảo.',
    uncertaintyNotice: null,
    legalBasis: [],
    penalties: [],
    requiredDocuments: [],
    procedureSteps: [],
    nextSteps: [],
    scenarioFacts: [],
    scenarioAnalysis: null,
    citations: [],
    sources: [],
  };

  it('renders standard answer as plain text', () => {
    render(<AiBubble response={baseResponse} />);
    expect(screen.getByText('Câu trả lời từ AI')).toBeInTheDocument();
  });

  it('renders disclaimer in muted style', () => {
    render(<AiBubble response={baseResponse} />);
    const disclaimer = screen.getByText('Thông tin tham khảo.');
    expect(disclaimer).toHaveClass('text-muted-foreground', 'text-xs');
  });

  it('renders scenario analysis as accordion when responseMode is SCENARIO_ANALYSIS', () => {
    const scenarioResponse: ChatAnswerResponse = {
      ...baseResponse,
      responseMode: 'SCENARIO_ANALYSIS',
      scenarioAnalysis: {
        facts: ['Sự kiện 1'],
        rule: 'Quy tắc 1',
        outcome: 'Hậu quả 1',
        actions: ['Hành động 1'],
        sources: [],
      },
    };
    render(<AiBubble response={scenarioResponse} />);
    expect(screen.getByText('Sự kiện được xác định')).toBeInTheDocument();
    expect(screen.getByText('Nguồn tài liệu')).toBeInTheDocument();
  });
});

describe('AiBubbleLoading', () => {
  it('renders 3 skeleton lines', () => {
    const { container } = render(<AiBubbleLoading />);
    // Skeleton elements use data-slot="skeleton" (shadcn/ui v4 pattern)
    const skeletons = container.querySelectorAll('[data-slot="skeleton"]');
    expect(skeletons.length).toBeGreaterThanOrEqual(3);
  });
});
