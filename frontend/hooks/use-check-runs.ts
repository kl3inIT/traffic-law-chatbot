'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getCheckRuns,
  triggerCheckRun,
  getCheckRunById,
  getCheckRunResults,
} from '@/lib/api/check-runs';
import type { CheckRun } from '@/types/api';

export function useCheckRuns() {
  return useQuery({
    queryKey: ['check-runs'],
    queryFn: getCheckRuns,
    refetchInterval: (query) => {
      const runs: CheckRun[] | undefined = query.state.data;
      if (runs?.some((r) => r.status === 'RUNNING')) return 5000;
      return false;
    },
  });
}

export function useTriggerCheckRun() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      chatModelId,
      evaluatorModelId,
    }: { chatModelId?: string; evaluatorModelId?: string } = {}) =>
      triggerCheckRun(chatModelId, evaluatorModelId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['check-runs'] }),
  });
}

export function useCheckRunDetail(id: string) {
  return useQuery({
    queryKey: ['check-runs', id],
    queryFn: () => getCheckRunById(id),
    enabled: !!id,
  });
}

export function useCheckRunResults(runId: string) {
  return useQuery({
    queryKey: ['check-runs', runId, 'results'],
    queryFn: () => getCheckRunResults(runId),
    enabled: !!runId,
  });
}
