'use client';

import { useState, useRef, useEffect, use } from 'react';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { UserBubble, AiBubble, AiBubbleLoading } from '@/components/chat/message-bubble';
import { ChatInput } from '@/components/chat/chat-input';
import { usePostMessage } from '@/hooks/use-chat';
import type { LocalMessage } from '@/hooks/use-chat';

// Next.js 16: params là Promise, dùng React.use() trong client component
export default function ThreadPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const postMessage = usePostMessage(id);
  const [messages, setMessages] = useState<LocalMessage[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (question: string) => {
    const userMsg: LocalMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: question,
      timestamp: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);

    try {
      const response = await postMessage.mutateAsync(question);
      const aiMsg: LocalMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.answer ?? '',
        response,
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, aiMsg]);
    } catch {
      // Lỗi hiển thị inline
    }
  };

  return (
    <div className="flex flex-1 flex-col h-screen">
      <ScrollArea className="flex-1 p-4">
        <div className="space-y-4 max-w-3xl mx-auto">
          {messages.map((msg) =>
            msg.role === 'user' ? (
              <UserBubble key={msg.id} content={msg.content} />
            ) : msg.response ? (
              <AiBubble key={msg.id} response={msg.response} />
            ) : null
          )}
          {postMessage.isPending && <AiBubbleLoading />}
          <div ref={scrollRef} />
        </div>

        {postMessage.isError && (
          <Alert variant="destructive" className="mt-4 max-w-3xl mx-auto">
            <AlertDescription>
              Không thể gửi tin nhắn. Vui lòng thử lại.
            </AlertDescription>
          </Alert>
        )}
      </ScrollArea>

      <ChatInput onSend={handleSend} isLoading={postMessage.isPending} />
    </div>
  );
}
