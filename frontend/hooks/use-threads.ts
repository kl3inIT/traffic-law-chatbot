'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchThreads, createThread } from '@/lib/api/chat';
import { queryKeys } from '@/lib/query-keys';

export function useThreads() {
  return useQuery({
    queryKey: queryKeys.threads,
    queryFn: fetchThreads,
  });
}

export function useCreateThread() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (question: string) => createThread(question),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.threads });
    },
  });
}
