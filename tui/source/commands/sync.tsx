import { useState, useEffect, useMemo } from 'react';
import { TableBrowser, type TableBrowserItem } from '../components/table-browser.js';
import { lsdb, syncAll, syncByTrackRootId, type TrackRootPreview } from '../api.js';

type SyncCommandProps = {
  onBack: () => void;
};

function toLabel(path: string): string {
  const segments = path.split(/[/\\]/);
  return segments.at(-1) ?? path;
}

export const SyncCommand = ({ onBack }: SyncCommandProps) => {
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | undefined>(undefined);
  const [trackRoots, setTrackRoots] = useState<TrackRootPreview[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [syncing, setSyncing] = useState(false);
  const [syncStatus, setSyncStatus] = useState<string | undefined>(undefined);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        setIsLoading(true);
        await syncAll();
        if (!alive) return;
      } catch (err) {
        if (!alive) return;
        setErrorMsg(err instanceof Error ? err.message : String(err));
      } finally {
        if (alive) setIsLoading(false);
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  const items: TableBrowserItem[] = useMemo(
    () =>
      trackRoots.map((tr, idx) => ({
        id: String(tr.id),
        label: `${idx + 1}. ${toLabel(tr.path)}`,
        rightText: tr.isSynced ? undefined : '(unsynced)',
      })),
    [trackRoots],
  );

  const handleEnter = async () => {
    if (syncing || isLoading || !trackRoots[selectedIndex]) return;
    setSyncing(true);
    setSyncStatus('同步中...');
    try {
      await syncByTrackRootId(trackRoots[selectedIndex].id);
      setSyncStatus('✅ 同步完成！');
      // 標記已同步
      setTrackRoots(prev => prev.map((tr, i) => (i === selectedIndex ? { ...tr, isSynced: true } : tr)));
    } catch (err) {
      setSyncStatus('❌ 同步失敗: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setSyncing(false);
      setTimeout(() => setSyncStatus(undefined), 2000);
    }
  };

  return (
    <TableBrowser
      title="選擇要同步的 Source Root"
      items={items}
      loading={isLoading}
      loadingText="正在載入專案來源..."
      errorText={errorMsg}
      escapeHint="返回主選單"
      onEscape={onBack}
      onEnter={handleEnter}
      statusText={syncStatus}
      leftHelpLines={['上下鍵選擇，Enter 同步', 'esc 返回主選單']}
      rightHelpLines={[]}
      // 讓 TableBrowser 支援上下移動
      onToggleCurrent={setSelectedIndex}
    />
  );
};
