import { apiGet, apiPost } from './client';
import type { ChatAnswerResponse, ChatThreadSummaryResponse } from '@/types/api';

export function fetchThreads(): Promise<ChatThreadSummaryResponse[]> {
  return apiGet('/api/v1/chat/threads');
}

export function createThread(question: string): Promise<ChatAnswerResponse> {
  return apiPost('/api/v1/chat/threads', { question });
}

export function postMessage(threadId: string, question: string): Promise<ChatAnswerResponse> {
  return apiPost(`/api/v1/chat/threads/${threadId}/messages`, { question });
}
