import { apiGet, apiPost } from './client';
import type { IngestionJobResponse, SourceSummaryResponse, PageResponse } from '@/types/api';

export function fetchSources(): Promise<PageResponse<SourceSummaryResponse>> {
  return apiGet('/api/v1/admin/sources');
}

export function fetchSourceIngestionJobs(sourceId: string): Promise<IngestionJobResponse[]> {
  return apiGet(`/api/v1/admin/sources/${sourceId}/ingestion-jobs`);
}

export function approveSource(sourceId: string, actedBy?: string): Promise<SourceSummaryResponse> {
  return apiPost(`/api/v1/admin/sources/${sourceId}/approve`, { actedBy: actedBy ?? 'admin' });
}

export function rejectSource(sourceId: string, actedBy?: string): Promise<SourceSummaryResponse> {
  return apiPost(`/api/v1/admin/sources/${sourceId}/reject`, { actedBy: actedBy ?? 'admin' });
}

export function activateSource(sourceId: string): Promise<SourceSummaryResponse> {
  return apiPost(`/api/v1/admin/sources/${sourceId}/activate`);
}

export function deactivateSource(sourceId: string): Promise<SourceSummaryResponse> {
  return apiPost(`/api/v1/admin/sources/${sourceId}/deactivate`);
}

export function reingestSource(sourceId: string): Promise<SourceSummaryResponse> {
  return apiPost(`/api/v1/admin/sources/${sourceId}/reingest`);
}
