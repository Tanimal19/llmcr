import { useEffect, useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { TextInput } from '@inkjs/ui';
import { CommandProps } from '../types.js';
import { chat, type ChatResponse } from '../api.js';

// ─── 1. 歷史訊息型態宣告 ───
interface Message {
  role: 'user' | 'assistant';
  text: string;
  prefix?: string; // 💡 顯式指定前綴（如 '>>> ' 或 '... '），讓渲染層徹底與邏輯解耦
}

// ─── 2. 獨立的高質感動態轉圈圈組件 ───
const ThinkingSpinner = () => {
  const [frameIndex, setFrameIndex] = useState(0);
  const frames = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'];

  useEffect(() => {
    const timer = setInterval(() => {
      setFrameIndex(prev => (prev + 1) % frames.length);
    }, 80);
    return () => clearInterval(timer);
  }, []);

  return (
    <Box>
      <Text color="gray">{frames[frameIndex]} Thinking...</Text>
    </Box>
  );
};

// ─── 3. 主對話控制核心 ───
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
      setMessages(prev => [...prev, { role: 'assistant', text: response.answer }]);
    } catch {
      setMessages(prev => [...prev, { role: 'assistant', text: "❌ 系統異常：與遠端 Java 服務中斷連線。" }]);
    } finally {
      setIsLoading(false);
    }
  };

  // ─── 流程 2：互動對話模式狀態機 ───
  const handleInteractiveSubmit = async (value: string) => {
    const trimmed = value.trim();

    if (isMultiline) {
      // 檢查本次輸入是否以 """ 結尾，代表要關閉多行並正式發送
      if (value.endsWith('"""')) {
        const lastLine = value.slice(0, -3);
        const finalBuffer = [...multilineBuffer];
        if (lastLine) finalBuffer.push(lastLine);

        // 將最後一行與閉合符印上終端機歷史
        setMessages(prev => [...prev, { role: 'user', text: value, prefix: '... ' }]);
        setIsLoading(true);
        setIsMultiline(false);
        setMultilineBuffer([]);
        setInputKey(prev => prev + 1);

        // 合併所有快取文字，以換行符串接，打包投遞給 Java API
        await executeChatApi(finalBuffer.join('\n'));
      } else {
        // 普通多行文字，持續累積進快取，並將前綴設為 '... '
        setMessages(prev => [...prev, { role: 'user', text: value, prefix: '... ' }]);
        setMultilineBuffer(prev => [...prev, value]);
        setInputKey(prev => prev + 1);
      }
      return;
    }

    // 🎯 核心分支 B：常規狀態下，偵測到 """ 啟動多行模式
    if (value.startsWith('"""')) {
      // 特殊狀況：如果單行直接閉合，例如輸入 """hello"""
      if (value.endsWith('"""') && value.length >= 6) {
        const inlineQuery = value.slice(3, -3);
        setMessages(prev => [...prev, { role: 'user', text: value, prefix: '>>> ' }]);
        setIsLoading(true);
        setInputKey(prev => prev + 1);
        await executeChatApi(inlineQuery);
      } else {
        // 正式切換為多行累積模式
        const firstLine = value.slice(3);
        setMessages(prev => [...prev, { role: 'user', text: value, prefix: '>>> ' }]);
        setIsMultiline(true);
        setMultilineBuffer(firstLine ? [firstLine] : []);
        setInputKey(prev => prev + 1);
      }
      return;
    }

    // 🎯 核心分支 C：空字串直接按 Enter 換行
    if (value.length === 0) {
      setMessages(prev => [...prev, { role: 'user', text: '', prefix: '>>> ' }]);
      setInputKey(prev => prev + 1);
      return;
    }

    // 🎯 核心分支 D：處理內建斜線指令 (Slash Commands)
    if (trimmed.startsWith('/')) {
      setMessages(prev => [...prev, { role: 'user', text: value, prefix: '>>> ' }]);
      setInputKey(prev => prev + 1);

      if (trimmed === '/q' || trimmed === '/exit') {
        onBack();
        return;
      }

      // 💡 實作 /clear 指令：重置全數狀態，清空終端面板環境
      if (trimmed === '/clear') {
        setMessages([]);
        setIsMultiline(false);
        setMultilineBuffer([]);
        return;
      }

      if (trimmed === '/?' || trimmed === '/help') {
        const helpMenu = [
          "Available Commands:",
          "  /clear          Clear session context",
          "  /q, /exit       Exit",
          "  /?, /help       Help",
          "",
          "Use \"\"\" to begin a multi-line message."
        ].join('\n');

        setMessages(prev => [...prev, { role: 'assistant', text: helpMenu }]);
        return;
      }

      setMessages(prev => [...prev, {
        role: 'assistant',
        text: `Unknown command '${value}'. Type /? for help`
      }]);
      return;
    }

    // 🎯 核心分支 E：標準單行常規對話
    setMessages(prev => [...prev, { role: 'user', text: value, prefix: '>>> ' }]);
    setIsLoading(true);
    setInputKey(prev => prev + 1);
    await executeChatApi(value);
  };

  return (
    <Box flexDirection="column" paddingX={0} paddingTop={0}>
      {/* 歷史對話瀑布流 */}
      <Box flexDirection="column">
        {messages.map((msg, i) => {
          // 優先使用明確指定的自訂前綴，若無則依角色判定（User 預設為 '>>> '，AI 無前綴靠左直落）
          const prefix = msg.prefix ?? (msg.role === 'user' ? '>>> ' : '');
          return (
            <Box key={i} flexDirection="row">
              {prefix ? <Text color="white">{prefix}</Text> : null}
              <Text color="white">{msg.text}</Text>
            </Box>
          );
        })}
      </Box>

      {/* 底層動態輸入輸入列 */}
      {isLoading ? (
        <ThinkingSpinner />
      ) : (
        <Box flexDirection="row">
          <Text color="white">{isMultiline ? '... ' : '>>> '}</Text>
          <TextInput
            key={inputKey}
            placeholder={isMultiline ? "" : "Send a message (/? for help)"}
            onSubmit={handleInteractiveSubmit}
          />
        </Box>
      )}
    </Box>
  );
};
