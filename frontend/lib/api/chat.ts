import { apiGet, apiPost } from './client';
import type {
  ChatAnswerResponse,
  ChatMessageResponse,
  ChatThreadSummaryResponse,
} from '@/types/api';

export function fetchThreads(): Promise<ChatThreadSummaryResponse[]> {
  return apiGet('/api/v1/chat/threads');
}

export function fetchThreadMessages(threadId: string): Promise<ChatMessageResponse[]> {
  return apiGet(`/api/v1/chat/threads/${threadId}/messages`);
}

export function createThread(question: string): Promise<ChatAnswerResponse> {
  return apiPost('/api/v1/chat/threads', { question });
}

export function postMessage(threadId: string, question: string): Promise<ChatAnswerResponse> {
  return apiPost(`/api/v1/chat/threads/${threadId}/messages`, { question });
}
