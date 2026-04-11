'use client';

import { useState, use } from 'react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { UserBubble, AiBubble, AiBubbleLoading } from '@/components/chat/message-bubble';
import { ChatInput } from '@/components/chat/chat-input';
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
  ConversationEmptyState,
} from '@/components/ai-elements/conversation';
import { usePostMessage } from '@/hooks/use-chat';
import type { LocalMessage } from '@/hooks/use-chat';

// Next.js 16: params là Promise, dùng React.use() trong client component
export default function ThreadPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const postMessage = usePostMessage(id);
  const [messages, setMessages] = useState<LocalMessage[]>([]);

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
      <Conversation className="flex-1">
        <ConversationContent className="max-w-3xl mx-auto w-full">
          {messages.length === 0 && !postMessage.isPending && (
            <ConversationEmptyState
              title="Bắt đầu cuộc hội thoại"
              description="Đặt câu hỏi về luật giao thông Việt Nam"
            />
          )}

          {messages.map((msg) =>
            msg.role === 'user' ? (
              <UserBubble key={msg.id} content={msg.content} />
            ) : msg.response ? (
              <AiBubble key={msg.id} response={msg.response} />
            ) : null
          )}

          {postMessage.isPending && <AiBubbleLoading />}
        </ConversationContent>

        <ConversationScrollButton />
      </Conversation>

      {postMessage.isError && (
        <Alert variant="destructive" className="mx-4 mb-2 max-w-3xl mx-auto">
          <AlertDescription>
            Không thể gửi tin nhắn. Vui lòng thử lại.
          </AlertDescription>
        </Alert>
      )}

      <ChatInput onSend={handleSend} isLoading={postMessage.isPending} />
    </div>
  );
}
