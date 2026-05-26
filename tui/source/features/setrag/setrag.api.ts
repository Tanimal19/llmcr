import { apiRequest, requireNonEmpty } from '#api/client.js';

export async function getRagScope(): Promise<Record<string, boolean>> {
  return apiRequest<Record<string, boolean>>('/getrag', {
    method: 'POST',
  });
}

export async function setRagScope(trackRootPaths: string[]): Promise<void> {
  requireNonEmpty(trackRootPaths, 'trackRootPaths must not be empty');
  await apiRequest<void>('/setrag', {
    method: 'POST',
    body: JSON.stringify({ trackRootPaths }),
  });
}
