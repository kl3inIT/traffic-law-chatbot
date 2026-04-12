'use client';

import { useQuery } from '@tanstack/react-query';
import { getChatLogs, getChatLogById, type ChatLogFilters } from '@/lib/api/chat-logs';

export function useChatLogs(filters: ChatLogFilters = {}) {
  return useQuery({
    queryKey: ['chat-logs', filters],
    queryFn: () => getChatLogs(filters),
  });
}

export function useChatLogDetail(id: string) {
  return useQuery({
    queryKey: ['chat-logs', id],
    queryFn: () => getChatLogById(id),
    enabled: !!id,
  });
}
