import { useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { MainMenu } from './menu.js';
import { ChatCommand } from '#features/chat/chat.cmd.js';
import { SetRagCommand } from '#features/chat/setrag.cmd.js';
import { ReviewCommand } from '#features/code-review/review.cmd.js';
import { LsDbCommand } from '#features/knowledge-base/lsdb.cmd.js';
import { SyncCommand } from '#features/knowledge-base/sync.cmd.js';

export type AppScreen = 'menu' | 'chat' | 'review' | 'sync' | 'lsdb' | 'setrag' | 'help';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<AppScreen>('menu');

  const handleBack = () => {
    setCurrentScreen('menu');
  };

  switch (currentScreen) {
    case 'menu': {
      return <MainMenu onSelect={setCurrentScreen} />;
    }

    case 'review': {
      return <ReviewCommand onBack={handleBack} />;
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

    case 'sync': {
      return <SyncCommand onBack={handleBack} />;
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
  }
}

const PlaceholderBackKey = ({ onBack }: { onBack: () => void }) => {
  useInput((_, key) => {
    if (key.escape) onBack();
  });
  return undefined;
};
