import { apiGet } from './client';
import type { ChunkReadinessResponse, IndexSummaryResponse } from '@/types/api';

export function fetchChunkReadiness(): Promise<ChunkReadinessResponse> {
  return apiGet('/api/v1/admin/chunks/readiness');
}

export function fetchIndexSummary(): Promise<IndexSummaryResponse> {
  return apiGet('/api/v1/admin/index/summary');
}
