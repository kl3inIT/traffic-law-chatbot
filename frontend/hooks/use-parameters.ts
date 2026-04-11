'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchParameterSets,
  createParameterSet,
  updateParameterSet,
  deleteParameterSet,
  activateParameterSet,
  copyParameterSet,
} from '@/lib/api/parameters';
import { queryKeys } from '@/lib/query-keys';
import type { CreateAiParameterSetRequest, UpdateAiParameterSetRequest } from '@/types/api';

export function useParameterSets() {
  return useQuery({
    queryKey: queryKeys.parameters,
    queryFn: fetchParameterSets,
  });
}

export function useCreateParameterSet() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateAiParameterSetRequest) => createParameterSet(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.parameters }),
  });
}

export function useUpdateParameterSet() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateAiParameterSetRequest }) =>
      updateParameterSet(id, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.parameters }),
  });
}

export function useDeleteParameterSet() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteParameterSet(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.parameters }),
  });
}

export function useActivateParameterSet() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => activateParameterSet(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.parameters }),
  });
}

export function useCopyParameterSet() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => copyParameterSet(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.parameters }),
  });
}
