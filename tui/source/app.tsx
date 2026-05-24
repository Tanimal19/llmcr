import { useState } from 'react';
import { Box, Text, useInput, useApp } from 'ink';
import { ChatCommand } from './commands/chat.js';
import { ReviewCommand } from './commands/review.js';
import { SetRagCommand } from './commands/setrag.js';
import { LsDbCommand } from './commands/lsdb.js';
import { SyncCommand } from './commands/sync.js';

// 主選單組件
const MainMenu = ({ onSelect }: { onSelect: (screen: string) => void }) => {
  const [activeIndex, setActiveIndex] = useState(0);
  const { exit } = useApp();

  const options = [
    { cmd: 'chat', desc: 'Enter chat mode', value: 'chat' },
    { cmd: 'review', desc: 'Generate code review', value: 'review_flow' },
    { cmd: 'lsdb', desc: 'List all database content', value: 'lsdb' },
    { cmd: 'sync', desc: 'Sync project data to database', value: 'sync_flow' },
    { cmd: 'setrag', desc: 'Modify RAG scope', value: 'setrag' },
    { cmd: 'help', desc: 'Show this command list', value: 'help' },
  ];

  useInput((input, key) => {
    if (key.upArrow) setActiveIndex(prev => Math.max(0, prev - 1));
    if (key.downArrow) setActiveIndex(prev => Math.min(options.length - 1, prev + 1));
    if (key.return) {
      const selected = options[activeIndex]?.value ?? 'help';
      onSelect(selected);
    }
    if (input === 'q' || key.escape) {
      exit();
    }
  });

  return (
    <Box flexDirection="column" paddingX={2} paddingTop={1}>
      {/* 頂部圓角資訊外框 (不設固定寬度，改由內容與外層彈性撐開) */}
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
                {/* 💡 這裡很關鍵：只固定「內部左側欄位」寬度，確保不論視窗多寬，後方描述永遠完美對齊 */}
                <Box width={26}>
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
          <Text color="green">●</Text> <Text color="white">Project loaded:</Text>{' '}
          <Text color="gray">C:/example_project/</Text>
        </Text>
        <Text>
          <Text color="green">●</Text> <Text color="white">Last synced:</Text>{' '}
          <Text color="gray">2026/04/17 22:04</Text>
        </Text>
      </Box>

      {/* 按鍵指南 Footer (自適應橫向拉滿) */}
      <Box
        flexDirection="column"
        borderStyle="single"
        borderTop={true}
        borderBottom={false}
        borderLeft={false}
        borderRight={false}
        borderColor="gray"
        paddingTop={1}
        marginTop={1}
      >
        <Box justifyContent="space-between">
          <Text color="gray">⇅ scroll</Text>
          <Text color="gray">enter select</Text>
        </Box>
        <Box justifyContent="space-between">
          <Text color="gray">q/esc exit</Text>
        </Box>
      </Box>
    </Box>
  );
};

interface ArgInputProps {
  title: string;
  placeholder: string;
  onSubmit: (value: string) => void;
  onCancel: () => void;
}

const ArgInput = ({ title, placeholder, onSubmit, onCancel }: ArgInputProps) => {
  const [inputValue, setInputValue] = useState('');

  useInput((input, key) => {
    if (key.escape) {
      onCancel();
      return;
    }
    if (key.return) {
      onSubmit(inputValue.trim() || placeholder);
      return;
    }
    if (key.backspace) {
      setInputValue(previous => previous.slice(0, -1));
      return;
    }
    if (input && !key.ctrl && !key.meta && input !== '\r' && input !== '\n' && input !== '\t' && input !== '\u001b[Z') {
      setInputValue(previous => previous + input);
    }
  });

  return (
    <Box flexDirection="column" paddingX={2} paddingTop={1}>
      <Text bold color="cyan">
        📝 {title}
      </Text>

      <Box borderStyle="round" borderColor="green" paddingX={1} marginY={1}>
        {inputValue ? (
          <Text color="white" bold>
            {inputValue}
            <Text color="green">┃</Text>
          </Text>
        ) : (
          <Text color="gray" dimColor>
            {placeholder} (直接按 Enter 使用此預設值)
          </Text>
        )}
      </Box>

      <Box
        flexDirection="column"
        borderStyle="single"
        borderTop={true}
        borderBottom={false}
        borderLeft={false}
        borderRight={false}
        borderColor="gray"
        paddingTop={1}
      >
        <Box justifyContent="space-between">
          <Text color="gray">enter confirm</Text>
          <Text color="gray">esc cancel</Text>
        </Box>
      </Box>
    </Box>
  );
};

// 核心路由器
export default function App() {
  const [currentScreen, setCurrentScreen] = useState<string>('menu');
  const [reviewArg, setReviewArg] = useState<string | undefined>(undefined);
  const [syncArg, setSyncArg] = useState<string | undefined>(undefined);

  const handleBack = () => {
    setReviewArg(undefined);
    setSyncArg(undefined);
    setCurrentScreen('menu');
  };

  switch (currentScreen) {
    case 'menu':
      return <MainMenu onSelect={setCurrentScreen} />;

    case 'review_flow':
      return (
        <ArgInput
          title="請輸入要進行 Code Review 的 Diff 檔案路徑"
          placeholder="./staged.diff"
          onCancel={handleBack}
          onSubmit={value => {
            setReviewArg(value);
            setCurrentScreen('review');
          }}
        />
      );
    case 'review':
      return <ReviewCommand onBack={handleBack} diffPath={reviewArg} />;

    case 'sync_flow':
      return (
        <ArgInput
          title="請輸入專案根目錄路徑 (Project Root)"
          placeholder="C:/example_project/"
          onCancel={handleBack}
          onSubmit={value => {
            setSyncArg(value);
            setCurrentScreen('sync');
          }}
        />
      );
    case 'sync':
      return <SyncCommand onBack={handleBack} targetPath={syncArg} />;
    case 'chat':
      return <ChatCommand onBack={handleBack} />;
    case 'setrag':
      return <SetRagCommand onBack={handleBack} />;
    case 'lsdb':
      return <LsDbCommand onBack={handleBack} />;

    case 'help':
      return (
        <Box flexDirection="column" padding={2}>
          <Text color="cyan" bold>
            💡 LLM-CR 系統幫助手冊
          </Text>
          <Text color="white">這是一個基於 Ink 驅動的自動化本地程式碼審查與動態 RAG 知識庫檢索終端介面。</Text>
          <Text color="gray">按 [Esc] 鍵安全返回極簡主選單</Text>
          <PlaceholderBackKey onBack={handleBack} />
        </Box>
      );

    default:
      return <Text>未知指令</Text>;
  }
}

const PlaceholderBackKey = ({ onBack }: { onBack: () => void }) => {
  useInput((_, key) => {
    if (key.escape) onBack();
  });
  return null;
};
