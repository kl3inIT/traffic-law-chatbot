import { apiGet, apiPost, apiPut, apiDelete } from './client';
import type { TrustPolicyResponse, CreateTrustPolicyRequest, UpdateTrustPolicyRequest } from '@/types/api';

const BASE = '/api/v1/admin/trust-policies';

export const fetchTrustPolicies = (): Promise<TrustPolicyResponse[]> => apiGet(BASE);
export const createTrustPolicy = (data: CreateTrustPolicyRequest): Promise<TrustPolicyResponse> => apiPost(BASE, data);
export const updateTrustPolicy = (id: string, data: UpdateTrustPolicyRequest): Promise<TrustPolicyResponse> => apiPut(`${BASE}/${id}`, data);
export const deleteTrustPolicy = (id: string): Promise<void> => apiDelete(`${BASE}/${id}`);
