export const queryKeys = {
  threads: ['chat', 'threads'] as const,
  thread: (id: string) => ['chat', 'threads', id] as const,
  sources: ['admin', 'sources'] as const,
  indexSummary: ['admin', 'index', 'summary'] as const,
  readiness: ['admin', 'index', 'readiness'] as const,
  parameters: ['admin', 'parameters'] as const,
  parameter: (id: string) => ['admin', 'parameters', id] as const,
};
