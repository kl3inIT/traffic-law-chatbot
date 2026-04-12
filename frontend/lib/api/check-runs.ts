import { apiGet, apiPost } from './client';
import type { CheckRun, CheckResult } from '@/types/api';

export async function getCheckRuns(): Promise<CheckRun[]> {
  return apiGet('/api/v1/admin/check-runs');
}

export async function triggerCheckRun(): Promise<{ runId: string }> {
  return apiPost('/api/v1/admin/check-runs/trigger');
}

export async function getCheckRunById(id: string): Promise<CheckRun> {
  return apiGet(`/api/v1/admin/check-runs/${id}`);
}

export async function getCheckRunResults(runId: string): Promise<CheckResult[]> {
  return apiGet(`/api/v1/admin/check-runs/${runId}/results`);
}
