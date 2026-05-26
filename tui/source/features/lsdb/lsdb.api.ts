import { apiRequest } from '#api/client.js';

export type SyncStatus = 'SYNCED' | 'REMOVED' | 'MODIFIED' | 'ADDED';

export type SourcePreview = {
  id: number | undefined;
  path: string;
  type: string;
  syncStatus: SyncStatus;
};

export type TrackRootPreview = {
  id: number;
  path: string;
  isSynced: boolean;
  lastSyncTime: string | undefined;
  sources: SourcePreview[];
};

export async function lsdb(): Promise<TrackRootPreview[]> {
  return apiRequest<TrackRootPreview[]>('/lsdb');
}
