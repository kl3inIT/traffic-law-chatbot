'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchTrustPolicies,
  createTrustPolicy,
  updateTrustPolicy,
  deleteTrustPolicy,
} from '@/lib/api/trust-policy';
import { queryKeys } from '@/lib/query-keys';
import type { CreateTrustPolicyRequest, UpdateTrustPolicyRequest } from '@/types/api';

export function useTrustPolicies() {
  return useQuery({
    queryKey: queryKeys.trustPolicies,
    queryFn: fetchTrustPolicies,
  });
}

export function useCreateTrustPolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateTrustPolicyRequest) => createTrustPolicy(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.trustPolicies }),
  });
}

export function useUpdateTrustPolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateTrustPolicyRequest }) =>
      updateTrustPolicy(id, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.trustPolicies }),
  });
}

export function useDeleteTrustPolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteTrustPolicy(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.trustPolicies }),
  });
}
