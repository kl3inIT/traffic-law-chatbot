export const queryKeys = {
  threads: ['chat', 'threads'] as const,
  thread: (id: string) => ['chat', 'threads', id] as const,
  sources: ['admin', 'sources'] as const,
  sourceIngestionJobs: (sourceId: string) =>
    ['admin', 'sources', sourceId, 'ingestion-jobs'] as const,
  indexSummary: ['admin', 'index', 'summary'] as const,
  readiness: ['admin', 'index', 'readiness'] as const,
  chunks: (sourceId?: string) => ['admin', 'index', 'chunks', sourceId ?? ''] as const,
  chunk: (chunkId: string) => ['admin', 'index', 'chunks', chunkId] as const,
  parameters: ['admin', 'parameters'] as const,
  parameter: (id: string) => ['admin', 'parameters', id] as const,
  trustPolicies: ['admin', 'trust-policies'] as const,
  trustPolicy: (id: string) => ['admin', 'trust-policies', id] as const,
};
