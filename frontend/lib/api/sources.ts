import { apiGet, apiPost } from './client';
import type { IngestionJobResponse, SourceSummaryResponse, PageResponse } from '@/types/api';

export function fetchSources(page = 0, size = 20): Promise<PageResponse<SourceSummaryResponse>> {
  return apiGet(`/api/v1/admin/sources?page=${page}&size=${size}`);
}

export function fetchAllSources(): Promise<PageResponse<SourceSummaryResponse>> {
  return apiGet('/api/v1/admin/sources?page=0&size=500');
}

export function fetchSourceIngestionJobs(sourceId: string): Promise<IngestionJobResponse[]> {
  return apiGet(`/api/v1/admin/sources/${sourceId}/ingestion-jobs`);
}

export function approveSource(sourceId: string): Promise<SourceSummaryResponse> {
  return apiPost(`/api/v1/admin/sources/${sourceId}/approve`, { actedBy: 'admin' });
}

export function rejectSource(sourceId: string): Promise<SourceSummaryResponse> {
  return apiPost(`/api/v1/admin/sources/${sourceId}/reject`, { actedBy: 'admin' });
}

export function bulkApproveSource(sourceIds: string[]): Promise<SourceSummaryResponse[]> {
  return Promise.all(sourceIds.map((id) => approveSource(id)));
}

export function bulkRejectSource(sourceIds: string[]): Promise<SourceSummaryResponse[]> {
  return Promise.all(sourceIds.map((id) => rejectSource(id)));
}

export function bulkActivateSource(sourceIds: string[]): Promise<SourceSummaryResponse[]> {
  return Promise.all(sourceIds.map((id) => activateSource(id)));
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
