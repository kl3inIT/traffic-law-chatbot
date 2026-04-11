import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { UserBubble, AiBubble, AiBubbleLoading } from '@/components/chat/message-bubble';
import type { ChatAnswerResponse } from '@/types/api';

describe('UserBubble', () => {
  it('renders user message content', () => {
    render(<UserBubble content="Xin chao" />);
    expect(screen.getByText('Xin chao')).toBeInTheDocument();
  });
});

describe('AiBubble', () => {
  const baseResponse: ChatAnswerResponse = {
    groundingStatus: 'GROUNDED',
    threadId: 'test-id',
    responseMode: 'STANDARD',
    answer: 'Cau tra loi tu AI',
    conclusion: null,
    disclaimer: 'Thong tin tham khao.',
    uncertaintyNotice: null,
    legalBasis: [],
    penalties: [],
    requiredDocuments: [],
    procedureSteps: [],
    nextSteps: [],
    pendingFacts: [],
    rememberedFacts: [],
    scenarioAnalysis: null,
    citations: [],
    sources: [],
  };

  it('renders standard answer as plain text', () => {
    render(<AiBubble response={baseResponse} />);
    expect(screen.getByText('Cau tra loi tu AI')).toBeInTheDocument();
  });

  it('renders disclaimer in muted style', () => {
    render(<AiBubble response={baseResponse} />);
    const disclaimer = screen.getByText('Thong tin tham khao.');
    expect(disclaimer).toHaveClass('text-muted-foreground', 'text-xs');
  });

  it('renders scenario analysis as accordion when responseMode is SCENARIO_ANALYSIS', () => {
    const scenarioResponse: ChatAnswerResponse = {
      ...baseResponse,
      responseMode: 'SCENARIO_ANALYSIS',
      scenarioAnalysis: {
        facts: ['Su kien 1'],
        rule: 'Quy tac 1',
        outcome: 'Hau qua 1',
        actions: ['Hanh dong 1'],
        sources: [],
      },
    };
    render(<AiBubble response={scenarioResponse} />);
    expect(screen.getByText('Su kien duoc xac dinh')).toBeInTheDocument();
    expect(screen.getByText('Nguon tai lieu')).toBeInTheDocument();
  });

  it('renders clarification pending facts', () => {
    const clarificationResponse: ChatAnswerResponse = {
      ...baseResponse,
      responseMode: 'CLARIFICATION_NEEDED',
      pendingFacts: [
        { factKey: 'vehicleType', question: 'Loai phuong tien?', explanation: 'Can biet' },
      ],
    };
    render(<AiBubble response={clarificationResponse} />);
    expect(screen.getByText(/Loai phuong tien\?/)).toBeInTheDocument();
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
