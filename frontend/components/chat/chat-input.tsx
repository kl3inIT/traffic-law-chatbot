'use client';

import {
  PromptInput,
  PromptInputBody,
  PromptInputFooter,
  PromptInputTextarea,
  PromptInputSubmit,
} from '@/components/ai-elements/prompt-input';

interface ChatInputProps {
  onSend: (message: string) => void;
  isLoading: boolean;
}

export function ChatInput({ onSend, isLoading }: ChatInputProps) {
  return (
    <div className="sticky bottom-0 border-t bg-background p-4">
      <PromptInput
        onSubmit={(msg) => {
          const text = msg.text.trim();
          if (text) onSend(text);
        }}
      >
        <PromptInputBody>
          <PromptInputTextarea
            placeholder="Nhập câu hỏi về luật giao thông..."
            disabled={isLoading}
          />
        </PromptInputBody>
        <PromptInputFooter>
          <PromptInputSubmit
            status={isLoading ? 'submitted' : 'ready'}
            disabled={isLoading}
          />
        </PromptInputFooter>
      </PromptInput>
    </div>
  );
}
