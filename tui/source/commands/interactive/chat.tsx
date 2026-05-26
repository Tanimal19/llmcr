import crypto from 'node:crypto';
import { useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { TextInput } from '@inkjs/ui';
import { type CommandProps } from '#commands/types.js';
import { chat, type ChatResponse } from '#api.js';
import { LoadingSpinner } from '#components';

// ─── 1. 歷史訊息型態宣告 ───
type Message = {
  id: string; // 💡 引入唯一 ID，用作 React 渲染時穩定的 Key
  role: 'user' | 'assistant';
  text: string;
  prefix?: string;
};

// ─── 2. 主對話控制核心 ───
export const ChatCommand = ({ onBack }: CommandProps) => {
  // ─── 狀態群組管理 ───
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputKey, setInputKey] = useState(0);
  const [isLoading, setIsLoading] = useState(false);

  // 💡 多行輸入狀態機
  const [isMultiline, setIsMultiline] = useState(false);
  const [multilineBuffer, setMultilineBuffer] = useState<string[]>([]);

  // 監聽 Esc 鍵滑順返回主選單
  useInput((_, key) => {
    if (key.escape) {
      onBack();
    }
  });

  // ─── 基礎核心：Java API 請求發送收攏 ───
  const executeChatApi = async (queryText: string) => {
    try {
      const response: ChatResponse = await chat(queryText);
      setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'assistant', text: response.answer }]);
    } catch {
      setMessages(prev => [
        ...prev,
        { id: crypto.randomUUID(), role: 'assistant', text: '❌ 系統異常：與遠端 Java 服務中斷連線。' },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  // ─── 輔助函數 1：處理已處於多行快取中的輸入 ───
  const processMultilineAccumulation = async (value: string) => {
    if (value.endsWith('"""')) {
      const lastLine = value.slice(0, -3);
      const finalBuffer = [...multilineBuffer];
      if (lastLine) finalBuffer.push(lastLine);

      setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'user', text: value, prefix: '... ' }]);
      setIsLoading(true);
      setIsMultiline(false);
      setMultilineBuffer([]);
      setInputKey(prev => prev + 1);

      await executeChatApi(finalBuffer.join('\n'));
      return;
    }

    setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'user', text: value, prefix: '... ' }]);
    setMultilineBuffer(prev => [...prev, value]);
    setInputKey(prev => prev + 1);
  };

  // ─── 輔助函數 2：處理宣告開啟多行模式的輸入 ───
  const processMultilineStart = async (value: string) => {
    if (value.endsWith('"""') && value.length >= 6) {
      const inlineQuery = value.slice(3, -3);
      setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'user', text: value, prefix: '>>> ' }]);
      setIsLoading(true);
      setInputKey(prev => prev + 1);
      await executeChatApi(inlineQuery);
      return;
    }

    const firstLine = value.slice(3);
    setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'user', text: value, prefix: '>>> ' }]);
    setIsMultiline(true);
    setMultilineBuffer(firstLine ? [firstLine] : []);
    setInputKey(prev => prev + 1);
  };

  // ─── 輔助函數 3：處理內建斜線指令 (Slash Commands) ───
  const processSlashCommand = (trimmed: string, value: string) => {
    setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'user', text: value, prefix: '>>> ' }]);
    setInputKey(prev => prev + 1);

    if (trimmed === '/q' || trimmed === '/exit') {
      onBack();
      return;
    }

    if (trimmed === '/clear') {
      setMessages([]);
      setIsMultiline(false);
      setMultilineBuffer([]);
      return;
    }

    if (trimmed === '/?' || trimmed === '/help') {
      const helpMenu = [
        'Available Commands:',
        '  /clear          Clear session context',
        '  /q, /exit       Exit',
        '  /?, /help       Help',
        '',
        'Use """ to begin a multi-line message.',
      ].join('\n');

      setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'assistant', text: helpMenu }]);
      return;
    }

    setMessages(prev => [
      ...prev,
      { id: crypto.randomUUID(), role: 'assistant', text: `Unknown command '${value}'. Type /? for help` },
    ]);
  };

  // 🎯 ─── 主入口流程 2：互動對話模式狀態機 ───
  const handleInteractiveSubmit = async (value: string) => {
    const trimmed = value.trim();

    // 1. 處理多行持續快取狀態
    if (isMultiline) {
      await processMultilineAccumulation(value);
      return;
    }

    // 2. 偵測啟動多行模式
    if (value.startsWith('"""')) {
      await processMultilineStart(value);
      return;
    }

    // 3. 空字串直接按 Enter 換行
    if (value.length === 0) {
      setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'user', text: '', prefix: '>>> ' }]);
      setInputKey(prev => prev + 1);
      return;
    }

    // 4. 處理斜線指令
    if (trimmed.startsWith('/')) {
      processSlashCommand(trimmed, value);
      return;
    }

    // 5. 標準單行常規對話
    setMessages(prev => [...prev, { id: crypto.randomUUID(), role: 'user', text: value, prefix: '>>> ' }]);
    setIsLoading(true);
    setInputKey(prev => prev + 1);
    await executeChatApi(value);
  };

  return (
    <Box flexDirection="column" paddingX={0} paddingTop={0}>
      {/* 歷史對話瀑布流 */}
      <Box flexDirection="column">
        {messages.map(msg => {
          // 優先使用明確指定的自訂前綴，若無則依角色判定
          const prefix = msg.prefix ?? (msg.role === 'user' ? '>>> ' : '');
          return (
            // 👇 完美移除 Array Index i，改用唯一且穩定的身分證 msg.id，符合 S6479 規範
            <Box key={msg.id} flexDirection="row">
              {prefix ? <Text color="cyan">{prefix}</Text> : null}
              <Text color="white">{msg.text}</Text>
            </Box>
          );
        })}
      </Box>

      {/* 底層動態輸入輸入列 */}
      {isLoading ? (
        <LoadingSpinner intervalMs={80} message="Thinking..." />
      ) : (
        <Box flexDirection="row">
          <Text color="cyan">{isMultiline ? '... ' : '>>> '}</Text>
          <TextInput
            key={inputKey}
            placeholder={isMultiline ? '' : 'Send a message (/? for help)'}
            onSubmit={value => {
              void handleInteractiveSubmit(value);
            }}
          />
        </Box>
      )}
    </Box>
  );
};
