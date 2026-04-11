import { apiGet } from './client';
import type {
  ChunkReadinessResponse,
  ChunkDetailResponse,
  ChunkSummaryResponse,
  IndexSummaryResponse,
  PageResponse,
} from '@/types/api';

export function fetchChunkReadiness(): Promise<ChunkReadinessResponse> {
  return apiGet('/api/v1/admin/chunks/readiness');
}

export function fetchIndexSummary(): Promise<IndexSummaryResponse> {
  return apiGet('/api/v1/admin/index/summary');
}

export function fetchChunks(
  sourceId?: string,
  page = 0,
  size = 20,
): Promise<PageResponse<ChunkSummaryResponse>> {
  const params = new URLSearchParams();
  if (sourceId) params.set('sourceId', sourceId);
  params.set('page', String(page));
  params.set('size', String(size));
  return apiGet(`/api/v1/admin/chunks?${params.toString()}`);
}

export function fetchChunk(chunkId: string): Promise<ChunkDetailResponse> {
  return apiGet(`/api/v1/admin/chunks/${chunkId}`);
}
