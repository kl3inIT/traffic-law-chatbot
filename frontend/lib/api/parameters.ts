import { apiGet, apiPost, apiPut, apiDelete } from './client';
import type { AiParameterSetResponse, CreateAiParameterSetRequest, UpdateAiParameterSetRequest } from '@/types/api';

export function fetchParameterSets(): Promise<AiParameterSetResponse[]> {
  return apiGet('/api/v1/admin/parameter-sets');
}

export function fetchParameterSet(id: string): Promise<AiParameterSetResponse> {
  return apiGet(`/api/v1/admin/parameter-sets/${id}`);
}

export function createParameterSet(data: CreateAiParameterSetRequest): Promise<AiParameterSetResponse> {
  return apiPost('/api/v1/admin/parameter-sets', data);
}

export function updateParameterSet(id: string, data: UpdateAiParameterSetRequest): Promise<AiParameterSetResponse> {
  return apiPut(`/api/v1/admin/parameter-sets/${id}`, data);
}

export function deleteParameterSet(id: string): Promise<void> {
  return apiDelete(`/api/v1/admin/parameter-sets/${id}`);
}

export function activateParameterSet(id: string): Promise<AiParameterSetResponse> {
  return apiPost(`/api/v1/admin/parameter-sets/${id}/activate`);
}

export function copyParameterSet(id: string): Promise<AiParameterSetResponse> {
  return apiPost(`/api/v1/admin/parameter-sets/${id}/copy`);
}
