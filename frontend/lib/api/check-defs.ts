import { apiGet, apiPost, apiPut, apiDelete } from './client';
import type { CheckDef } from '@/types/api';

export interface CheckDefPayload {
  question: string;
  referenceAnswer: string;
  category?: string;
  active?: boolean;
}

export async function getCheckDefs(): Promise<CheckDef[]> {
  return apiGet('/api/v1/admin/check-defs');
}

export async function createCheckDef(payload: CheckDefPayload): Promise<CheckDef> {
  return apiPost('/api/v1/admin/check-defs', payload);
}

export async function updateCheckDef(id: string, payload: CheckDefPayload): Promise<CheckDef> {
  return apiPut(`/api/v1/admin/check-defs/${id}`, payload);
}

export async function deleteCheckDef(id: string): Promise<void> {
  return apiDelete(`/api/v1/admin/check-defs/${id}`);
}
