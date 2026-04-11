import { describe, it, expect } from 'vitest';
import { queryKeys } from '@/lib/query-keys';

describe('smoke tests', () => {
  it('query keys are defined', () => {
    expect(queryKeys.threads).toEqual(['chat', 'threads']);
    expect(queryKeys.sources).toEqual(['admin', 'sources']);
    expect(queryKeys.parameters).toEqual(['admin', 'parameters']);
  });

  it('api types are importable', async () => {
    const types = await import('@/types/api');
    expect(types).toBeDefined();
  });
});
