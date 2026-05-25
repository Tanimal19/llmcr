import { useEffect, useMemo, useState } from 'react';
import { Box, Text } from 'ink';
import { lsdb } from '../api.js';
import { TableBrowser, type TableBrowserItem } from '../components/table-browser.js';
import { type CommandProps } from '../types.js';

const STATUS_STYLE: Record<
  'SYNCED' | 'REMOVED' | 'ADDED' | 'MODIFIED',
  { color: string; prefix?: string; legend: string }
> = {
  SYNCED: { color: 'white', legend: 'SYNCED   : <name>' },
  REMOVED: { color: 'red', prefix: '- ', legend: 'REMOVED  : - <name>' },
  ADDED: { color: 'green', prefix: '+ ', legend: 'ADDED    : + <name>' },
  MODIFIED: { color: 'green', prefix: '± ', legend: 'MODIFIED : ± <name>' },
};

function toLabel(path: string): string {
  // 修正點：為正則表達式加上 v 旗標
  const segments = path.split(/[\/\\\\]/v);
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
            content: (
              <>
                {STATUS_STYLE[source.syncStatus].prefix ? (
                  <Text color={STATUS_STYLE[source.syncStatus].color}>{STATUS_STYLE[source.syncStatus].prefix}</Text>
                ) : null}
                <Text color={STATUS_STYLE[source.syncStatus].color}>{`${index + 1}. ${toLabel(source.path)}`}</Text>
                {source.syncStatus === 'SYNCED' ? null : (
                  <Text color={STATUS_STYLE[source.syncStatus].color}>{` (${source.syncStatus.toLowerCase()})`}</Text>
                )}
              </>
            ),
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
      header={
        <>
          <Text color="white" bold>
            {currentTable ?? 'No track roots'}
          </Text>
          {subtitle ? <Text color="gray">{subtitle}</Text> : null}
        </>
      }
      items={currentItems}
      footer={
        <>
          <Box justifyContent="space-between">
            <Text color="gray">shift+tab switch track root</Text>
            <Text color="gray">enter/esc back</Text>
          </Box>
          <Box justifyContent="space-between" marginBottom={1}>
            <Text color="gray">up/down move</Text>
            <Text color="gray" />
          </Box>
          <Text color="gray">Status format:</Text>
          <Text color={STATUS_STYLE.SYNCED.color}>{STATUS_STYLE.SYNCED.legend}</Text>
          <Text color={STATUS_STYLE.REMOVED.color}>{STATUS_STYLE.REMOVED.legend}</Text>
          <Text color={STATUS_STYLE.ADDED.color}>{STATUS_STYLE.ADDED.legend}</Text>
          <Text color={STATUS_STYLE.MODIFIED.color}>{STATUS_STYLE.MODIFIED.legend}</Text>
          {errorMsg ? (
            <Text color="red" bold>
              {errorMsg}
            </Text>
          ) : null}
        </>
      }
      loading={isLoading}
      loadingText="Loading track roots..."
      onEscape={leave}
      onEnter={() => {
        leave();
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
