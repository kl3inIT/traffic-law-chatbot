'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchSources,
  fetchSourceIngestionJobs,
  approveSource,
  rejectSource,
  activateSource,
  deactivateSource,
  reingestSource,
} from '@/lib/api/sources';
import { queryKeys } from '@/lib/query-keys';

export function useSources() {
  return useQuery({
    queryKey: queryKeys.sources,
    queryFn: fetchSources,
  });
}

function useSourceMutation(mutationFn: (id: string) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.sources }),
  });
}

export function useApproveSource() {
  return useSourceMutation(approveSource);
}
export function useRejectSource() {
  return useSourceMutation(rejectSource);
}
export function useActivateSource() {
  return useSourceMutation(activateSource);
}
export function useDeactivateSource() {
  return useSourceMutation(deactivateSource);
}
export function useReingestSource() {
  return useSourceMutation(reingestSource);
}

export function useSourceIngestionJobs(sourceId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.sourceIngestionJobs(sourceId),
    queryFn: () => fetchSourceIngestionJobs(sourceId),
    enabled,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data || data.length === 0) return false;
      const latest = data[0];
      return latest.status === 'RUNNING' || latest.status === 'QUEUED' ? 5000 : false;
    },
  });
}
