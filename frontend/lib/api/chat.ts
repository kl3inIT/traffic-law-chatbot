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

export function createThread(question: string, modelId?: string): Promise<ChatAnswerResponse> {
  return apiPost('/api/v1/chat/threads', { question, modelId });
}

export function postMessage(threadId: string, question: string, modelId?: string): Promise<ChatAnswerResponse> {
  return apiPost(`/api/v1/chat/threads/${threadId}/messages`, { question, modelId });
}
