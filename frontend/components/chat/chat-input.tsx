'use client';

import { useState, useRef, useCallback, KeyboardEvent } from 'react';
import { Send } from 'lucide-react';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';

interface ChatInputProps {
  onSend: (message: string) => void;
  isLoading: boolean;
}

export function ChatInput({ onSend, isLoading }: ChatInputProps) {
  const [value, setValue] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = useCallback(() => {
    const trimmed = value.trim();
    if (!trimmed || isLoading) return;
    onSend(trimmed);
    setValue('');
    // Reset textarea height
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  }, [value, isLoading, onSend]);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter to send, Shift+Enter for newline per UI-SPEC
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // Auto-resize up to 5 rows then scroll per UI-SPEC
  const handleInput = () => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = 'auto';
    const maxHeight = 5 * 24; // ~5 rows at ~24px line height
    textarea.style.height = Math.min(textarea.scrollHeight, maxHeight) + 'px';
  };

  return (
    <div className="sticky bottom-0 border-t bg-background p-4">
      <div className="flex gap-2 items-end">
        <Textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => { setValue(e.target.value); handleInput(); }}
          onKeyDown={handleKeyDown}
          placeholder="Nhap cau hoi ve luat giao thong..."
          className="min-h-[44px] resize-none"
          rows={1}
          disabled={isLoading}
        />
        <Button
          variant="default"
          size="icon"
          onClick={handleSend}
          disabled={!value.trim() || isLoading}
          className="min-h-[44px] min-w-[44px]"
          title="Gui tin nhan"
        >
          <Send className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
