'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getCheckDefs,
  createCheckDef,
  updateCheckDef,
  deleteCheckDef,
  type CheckDefPayload,
} from '@/lib/api/check-defs';

export function useCheckDefs() {
  return useQuery({ queryKey: ['check-defs'], queryFn: getCheckDefs });
}

export function useCreateCheckDef() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createCheckDef,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['check-defs'] }),
  });
}

export function useUpdateCheckDef() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: CheckDefPayload }) =>
      updateCheckDef(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['check-defs'] }),
  });
}

export function useDeleteCheckDef() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteCheckDef,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['check-defs'] }),
  });
}
