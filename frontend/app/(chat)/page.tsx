'use client';

import { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { UserBubble, AiBubble, AiBubbleLoading } from '@/components/chat/message-bubble';
import { ChatInput } from '@/components/chat/chat-input';
import { useCreateThread } from '@/hooks/use-chat';
import type { LocalMessage } from '@/hooks/use-chat';

export default function ChatPage() {
  const router = useRouter();
  const createThread = useCreateThread();
  const [messages, setMessages] = useState<LocalMessage[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll on new messages
  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (question: string) => {
    // Add user message to local state
    const userMsg: LocalMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: question,
      timestamp: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);

    try {
      const response = await createThread.mutateAsync(question);
      // Add AI response
      const aiMsg: LocalMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.answer ?? '',
        response,
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, aiMsg]);
      // Navigate to thread view for subsequent messages
      router.push(`/threads/${response.threadId}`);
    } catch {
      // Error handled inline
    }
  };

  return (
    <div className="flex flex-1 flex-col h-screen">
      <ScrollArea className="flex-1 p-4">
        {messages.length === 0 && (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <h1 className="text-3xl font-semibold">Chatbot Luat Giao thong</h1>
              <p className="text-sm text-muted-foreground mt-2">
                Dat cau hoi ve luat giao thong Viet Nam
              </p>
            </div>
          </div>
        )}

        <div className="space-y-4 max-w-3xl mx-auto">
          {messages.map((msg) =>
            msg.role === 'user' ? (
              <UserBubble key={msg.id} content={msg.content} />
            ) : msg.response ? (
              <AiBubble key={msg.id} response={msg.response} />
            ) : null
          )}
          {createThread.isPending && <AiBubbleLoading />}
          <div ref={scrollRef} />
        </div>

        {createThread.isError && (
          <Alert variant="destructive" className="mt-4 max-w-3xl mx-auto">
            <AlertDescription>
              Khong the gui tin nhan. Vui long thu lai.
            </AlertDescription>
          </Alert>
        )}
      </ScrollArea>

      <ChatInput onSend={handleSend} isLoading={createThread.isPending} />
    </div>
  );
}
