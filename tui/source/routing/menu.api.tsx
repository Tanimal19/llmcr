import { apiRequest } from '#api/client.js';

export type InfoResponse = {
  configPath: string;
  config: unknown;
  lastSyncTime: string | undefined;
};

export async function info(): Promise<InfoResponse> {
  return apiRequest<InfoResponse>('/info');
}
