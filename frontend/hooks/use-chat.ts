'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createThread, fetchThreadMessages, postMessage } from '@/lib/api/chat';
import { queryKeys } from '@/lib/query-keys';
import type { ChatAnswerResponse } from '@/types/api';

// Message in the local conversation state
export interface LocalMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  response?: ChatAnswerResponse;
  timestamp: string;
}

export function useThreadMessages(threadId: string) {
  return useQuery({
    queryKey: [...queryKeys.threads, threadId, 'messages'],
    queryFn: () => fetchThreadMessages(threadId),
    staleTime: 0,
  });
}

export function useCreateThread() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ question, modelId }: { question: string; modelId?: string }) =>
      createThread(question, modelId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.threads });
    },
  });
}

export function usePostMessage(threadId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ question, modelId }: { question: string; modelId?: string }) =>
      postMessage(threadId, question, modelId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.threads });
    },
  });
}
