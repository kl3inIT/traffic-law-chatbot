'use client';

import { useState, use } from 'react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { UserBubble, AiBubble, AiBubbleLoading } from '@/components/chat/message-bubble';
import { ChatInput } from '@/components/chat/chat-input';
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from '@/components/ai-elements/conversation';
import { Skeleton } from '@/components/ui/skeleton';
import { usePostMessage, useThreadMessages } from '@/hooks/use-chat';
import type { LocalMessage } from '@/hooks/use-chat';

export default function ThreadPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const postMessage = usePostMessage(id);
  const { data: history, isLoading: historyLoading } = useThreadMessages(id);

  // Messages sent in this session — starts empty on every navigation (fresh mount)
  const [sessionMessages, setSessionMessages] = useState<LocalMessage[]>([]);

  const handleSend = async (question: string) => {
    const userMsg: LocalMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: question,
      timestamp: new Date().toISOString(),
    };
    setSessionMessages((prev) => [...prev, userMsg]);

    try {
      const response = await postMessage.mutateAsync(question);
      const aiMsg: LocalMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.answer ?? '',
        response,
        timestamp: new Date().toISOString(),
      };
      setSessionMessages((prev) => [...prev, aiMsg]);
    } catch {
      // error shown via postMessage.isError below
    }
  };

  return (
    <div className="flex h-screen flex-1 flex-col">
      <Conversation className="flex-1">
        <ConversationContent className="mx-auto w-full max-w-3xl">
          {/* History loading skeletons */}
          {historyLoading && (
            <div className="space-y-4">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-16 w-full rounded-lg" />
              ))}
            </div>
          )}

          {/* Historical messages fetched from server */}
          {history?.map((msg) =>
            msg.role === 'USER' ? (
              <UserBubble key={msg.id} content={msg.content} />
            ) : (
              // Historical AI messages: plain text only — full structured response is not persisted
              <div key={msg.id} className="flex gap-2">
                <div className="bg-card max-w-[85%] rounded-lg border p-4 text-sm whitespace-pre-wrap">
                  {msg.content}
                </div>
              </div>
            ),
          )}

          {/* New messages from this navigation session */}
          {sessionMessages.map((msg) =>
            msg.role === 'user' ? (
              <UserBubble key={msg.id} content={msg.content} />
            ) : msg.response ? (
              <AiBubble key={msg.id} response={msg.response} />
            ) : null,
          )}

          {postMessage.isPending && <AiBubbleLoading />}
        </ConversationContent>

        <ConversationScrollButton />
      </Conversation>

      {postMessage.isError && (
        <Alert variant="destructive" className="mx-4 mx-auto mb-2 max-w-3xl">
          <AlertDescription>Không thể gửi tin nhắn. Vui lòng thử lại.</AlertDescription>
        </Alert>
      )}

      <ChatInput onSend={handleSend} isLoading={postMessage.isPending} />
    </div>
  );
}
