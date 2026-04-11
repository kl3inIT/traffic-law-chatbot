'use client';

import { useQuery } from '@tanstack/react-query';
import { fetchChunkReadiness, fetchChunk, fetchChunks, fetchIndexSummary } from '@/lib/api/chunks';
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

export function useChunks(sourceId?: string, page = 0, size = 20) {
  return useQuery({
    queryKey: queryKeys.chunks(sourceId),
    queryFn: () => fetchChunks(sourceId, page, size),
  });
}

export function useChunk(chunkId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.chunk(chunkId),
    queryFn: () => fetchChunk(chunkId),
    enabled,
  });
}
