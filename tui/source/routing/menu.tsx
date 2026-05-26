import { useState, useEffect } from 'react';
import { Box, Text, useInput, useApp } from 'ink';
import { info } from './menu.api.js'; // 就近引入自己模組的 API
import { type AppScreen } from '#routing/router.js';

type MainMenuProps = {
  onSelect: (screen: AppScreen) => void; // 🎯 這裡不再是 string，而是限制範圍的 AppScreen
};

type MenuOption = {
  cmd: string;
  desc: string;
  value: AppScreen; // 強迫 value 必須是合法的畫面名稱
};

export const MainMenu = ({ onSelect }: MainMenuProps) => {
  const [activeIndex, setActiveIndex] = useState(0);
  const { exit } = useApp();

  // ─── 🧠 把原本在 App 的狀態與副作用移到這裡 ───
  const [configLoaded, setconfigLoaded] = useState('Loading...');
  const [lastSynced, setLastSynced] = useState('Loading...');
  const [isInfoLoading, setIsInfoLoading] = useState(true);
  const [infoError, setInfoError] = useState<string | undefined>(undefined);

  useEffect(() => {
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
  }, []); // 空陣列即可，只有當選單元件被渲染（即回到主選單）時才觸發

  const options: MenuOption[] = [
    { cmd: 'review', desc: 'Generate code review', value: 'review' },
    { cmd: 'chat', desc: 'Enter chat mode', value: 'chat' },
    { cmd: 'setrag', desc: 'Modify RAG scope', value: 'setrag' },
    { cmd: 'lsdb', desc: 'List all database content', value: 'lsdb' },
    { cmd: 'sync', desc: 'Sync project data to database', value: 'sync' },
    { cmd: 'help', desc: 'Show this command list', value: 'help' },
  ];

  useInput((input, key) => {
    if (key.upArrow) setActiveIndex(prev => (prev - 1 + options.length) % options.length);
    if (key.downArrow) setActiveIndex(prev => (prev + 1) % options.length);
    if (key.return) {
      const selected = options[activeIndex]?.value ?? 'help';
      onSelect(selected); // 👍 這裡傳出去的絕對是安全的型態
    }

    if (input === 'q' || key.escape) exit();
  });

  return (
    <Box flexDirection="column" paddingX={2} paddingTop={1}>
      {/* 頂部圓角資訊外框 */}
      <Box borderStyle="round" borderColor="gray" flexDirection="column" paddingX={1}>
        <Text bold color="cyan">
          LLM-CR v0.0.1
        </Text>
        <Text color="gray">Select a command to get started.</Text>
      </Box>

      {/* 可用指令列表區段 */}
      <Box flexDirection="column" marginTop={1}>
        <Text bold color="white">
          ● Available Commands:
        </Text>
        <Box flexDirection="column" marginTop={0}>
          {options.map((opt, idx) => {
            const isSelected = idx === activeIndex;
            return (
              <Box key={opt.value}>
                <Box width={4}>
                  <Text color={isSelected ? 'green' : 'white'} bold={isSelected}>
                    {isSelected ? '  >' : '   '}
                  </Text>
                </Box>
                <Box width={10}>
                  <Text color={isSelected ? 'green' : 'white'} bold={isSelected}>
                    {opt.cmd}
                  </Text>
                </Box>
                <Box>
                  <Text color={isSelected ? 'white' : 'gray'}>{opt.desc}</Text>
                </Box>
              </Box>
            );
          })}
        </Box>
      </Box>

      {/* 項目狀況中繼資料 */}
      <Box flexDirection="column" marginTop={1}>
        <Text>
          <Text color="green">●</Text> <Text color="white">Config loaded:</Text>{' '}
          <Text color="gray">{configLoaded}</Text>
        </Text>
        <Text>
          <Text color="green">●</Text> <Text color="white">Last synced:</Text> <Text color="gray">{lastSynced}</Text>
        </Text>
        {isInfoLoading ? <Text color="gray">Loading info metadata...</Text> : undefined}
        {!isInfoLoading && infoError ? <Text color="red">Failed to load /info: {infoError}</Text> : undefined}
      </Box>

      {/* 按鍵指南 Footer */}
      <Box
        flexDirection="column"
        borderStyle="single"
        borderTop={true}
        borderBottom={false}
        borderLeft={false}
        borderRight={false}
        borderColor="gray"
        paddingTop={0}
        marginTop={0}
      >
        <Box justifyContent="space-between">
          <Text color="gray">⇅ scroll</Text>
          <Text color="gray">q/esc exit</Text>
        </Box>
      </Box>
    </Box>
  );
};
