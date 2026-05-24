import { useEffect, useMemo, useState } from 'react';
import { lsdb } from '../api.js';
import { TableBrowser, type TableBrowserItem } from '../components/tableBrowser.js';
import { type CommandProps } from '../types.js';

function toLabel(path: string): string {
  const segments = path.split(/[/\\]/);
  return segments.at(-1) ?? path;
}

export const LsDbCommand = ({ onBack }: CommandProps) => {
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | undefined>(undefined);
  const [tableKeys, setTableKeys] = useState<string[]>([]);
  const [tableItems, setTableItems] = useState<Record<string, TableBrowserItem[]>>({});
  const [tableSyncStatus, setTableSyncStatus] = useState<Record<string, boolean>>({});
  const [tableIndex, setTableIndex] = useState(0);

  const leave = () => {
    onBack();
  };

  useEffect(() => {
    let alive = true;

    (async () => {
      try {
        const previews = await lsdb();
        if (!alive) {
          return;
        }

        const nextKeys = previews.map(preview => preview.path);
        const nextItems: Record<string, TableBrowserItem[]> = {};
        const nextSyncStatus: Record<string, boolean> = {};

        for (const preview of previews) {
          nextItems[preview.path] = preview.sources.map((source, index) => ({
            id: String(source.id ?? source.path),
            label: `${index + 1}. ${toLabel(source.path)}`,
            rightText: source.syncStatus === 'SYNCED' ? undefined : `(${source.syncStatus.toLowerCase()})`,
          }));
          nextSyncStatus[preview.path] = preview.isSynced;
        }

        setTableKeys(nextKeys);
        setTableItems(nextItems);
        setTableSyncStatus(nextSyncStatus);
      } catch (error) {
        if (!alive) {
          return;
        }

        setErrorMsg(error instanceof Error ? error.message : String(error));
      } finally {
        if (alive) {
          setIsLoading(false);
        }
      }
    })();

    return () => {
      alive = false;
    };
  }, []);

  const safeTableIndex = tableKeys.length === 0 ? 0 : Math.min(tableIndex, tableKeys.length - 1);
  const currentTable = tableKeys[safeTableIndex];
  const currentItems = currentTable ? (tableItems[currentTable] ?? []) : [];
  const subtitle = useMemo(() => {
    if (!currentTable) {
      return undefined;
    }

    const syncLabel = tableSyncStatus[currentTable] ? 'Synced' : 'Unsynced';
    const itemCount = currentItems.length;
    const itemLabel = itemCount === 1 ? 'source' : 'sources';

    return `${syncLabel} · ${itemCount} ${itemLabel}`;
  }, [currentItems.length, currentTable, tableSyncStatus]);

  return (
    <TableBrowser
      title={currentTable ?? 'No track roots'}
      subtitle={subtitle}
      items={currentItems}
      loading={isLoading}
      loadingText="Loading track roots..."
      errorText={errorMsg}
      errorEnterAction="escape"
      escapeHint={'back'}
      leftHelpLines={['shift+tab switch track root', 'up/down move']}
      rightHelpLines={['esc back']}
      onEscape={leave}
      onEnter={() => {
}}
      onSwitchTable={() => {
        if (tableKeys.length === 0) {
          return;
        }

        setTableIndex(previous => (previous + 1) % tableKeys.length);
      }}
    />
  );
};
