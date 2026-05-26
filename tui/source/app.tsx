import { useEffect, useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { info } from '#api.js';
import { ChatCommand } from '#commands/interactive/chat.js';
import { ReviewCommand } from '#commands/stream/review.js';
import { SetRagCommand } from '#commands/database/setrag.js';
import { LsDbCommand } from '#commands/database/lsdb.js';
import { SyncCommand } from '#commands/stream/sync.js';
import { MainMenu } from '#menu.js';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<string>('menu');
  const [configLoaded, setconfigLoaded] = useState('Loading...');
  const [lastSynced, setLastSynced] = useState('Loading...');
  const [isInfoLoading, setIsInfoLoading] = useState(true);
  const [infoError, setInfoError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (currentScreen !== 'menu') return;

    let disposed = false;
    const loadInfo = async (): Promise<void> => {
      setIsInfoLoading(true);
      setInfoError(undefined);
      try {
        const result = await info();
        if (disposed) return;
        setconfigLoaded(result.configPath || 'N/A');
        setLastSynced(result.lastSyncTime ? String(result.lastSyncTime) : 'N/A');
      } catch (error) {
        if (disposed) return;
        setconfigLoaded('N/A');
        setLastSynced('N/A');
        setInfoError(error instanceof Error ? error.message : String(error));
      } finally {
        if (!disposed) setIsInfoLoading(false);
      }
    };

    void loadInfo();
    return () => {
      disposed = true;
    };
  }, [currentScreen]);

  const handleBack = () => {
    setCurrentScreen('menu'); // 狀態清空收攏到各 Command 內部，App 只管切回主選單
  };

  switch (currentScreen) {
    case 'menu': {
      return (
        <MainMenu
          onSelect={setCurrentScreen}
          configLoaded={configLoaded}
          lastSynced={lastSynced}
          isInfoLoading={isInfoLoading}
          infoError={infoError}
        />
      );
    }

    case 'review': {
      // 外殼路由器不再關心參數，直接把控制權交給 Review
      return <ReviewCommand onBack={handleBack} />;
    }

    case 'sync': {
      return <SyncCommand onBack={handleBack} />;
    }

    case 'chat': {
      return <ChatCommand onBack={handleBack} />;
    }

    case 'setrag': {
      return <SetRagCommand onBack={handleBack} />;
    }

    case 'lsdb': {
      return <LsDbCommand onBack={handleBack} />;
    }

    case 'help': {
      return (
        <Box flexDirection="column" padding={2}>
          <Text color="cyan" bold>
            LLM-CR help
          </Text>
          <Text color="gray">[Esc] back to menu</Text>
          <PlaceholderBackKey onBack={handleBack} />
        </Box>
      );
    }

    default: {
      return <Text>Unknown command</Text>;
    }
  }
}

const PlaceholderBackKey = ({ onBack }: { onBack: () => void }) => {
  useInput((_, key) => {
    if (key.escape) onBack();
  });
  return undefined;
};
