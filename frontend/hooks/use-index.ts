'use client';

import { useQuery } from '@tanstack/react-query';
import { fetchChunkReadiness, fetchIndexSummary } from '@/lib/api/chunks';
import { queryKeys } from '@/lib/query-keys';

export function useChunkReadiness() {
  return useQuery({
    queryKey: queryKeys.readiness,
    queryFn: fetchChunkReadiness,
  });
}

export function useIndexSummary() {
  return useQuery({
    queryKey: queryKeys.indexSummary,
    queryFn: fetchIndexSummary,
  });
}
