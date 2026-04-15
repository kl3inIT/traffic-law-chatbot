import { apiGet, apiPost } from './client';
import type { CheckRun, CheckResult } from '@/types/api';

export async function getCheckRuns(): Promise<CheckRun[]> {
  return apiGet('/api/v1/admin/check-runs');
}

export async function triggerCheckRun(
  chatModelId?: string,
  evaluatorModelId?: string,
): Promise<{ runId: string }> {
  const params = new URLSearchParams();
  if (chatModelId) params.set('chatModelId', chatModelId);
  if (evaluatorModelId) params.set('evaluatorModelId', evaluatorModelId);
  const qs = params.toString();
  return apiPost(`/api/v1/admin/check-runs/trigger${qs ? `?${qs}` : ''}`);
}

export async function getCheckRunById(id: string): Promise<CheckRun> {
  return apiGet(`/api/v1/admin/check-runs/${id}`);
}

export async function getCheckRunResults(runId: string): Promise<CheckResult[]> {
  return apiGet(`/api/v1/admin/check-runs/${runId}/results`);
}
