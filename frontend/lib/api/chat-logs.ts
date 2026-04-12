import { apiGet } from './client';
import type { ChatLogPage, ChatLogDetail, GroundingStatus } from '@/types/api';

export interface ChatLogFilters {
  groundingStatus?: GroundingStatus;
  from?: string;
  to?: string;
  q?: string;
  page?: number;
  size?: number;
}

export async function getChatLogs(filters: ChatLogFilters = {}): Promise<ChatLogPage> {
  const params = new URLSearchParams();
  if (filters.groundingStatus) params.set('groundingStatus', filters.groundingStatus);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.q) params.set('q', filters.q);
  if (filters.page !== undefined) params.set('page', String(filters.page));
  if (filters.size !== undefined) params.set('size', String(filters.size));
  params.set('sort', 'createdDate,desc');
  return apiGet(`/api/v1/admin/chat-logs?${params.toString()}`);
}

export async function getChatLogById(id: string): Promise<ChatLogDetail> {
  return apiGet(`/api/v1/admin/chat-logs/${id}`);
}
