import { useState } from 'react';
import { Box, Text, useInput, type Key } from 'ink';

type ArgInputProps = {
  title: string;
  placeholder: string;
  usePlaceholderOnEmpty?: boolean;
  onSubmit: (value: string) => void;
  onCancel: () => void;
};

// ─── 💡 抽離常規字元合法性校驗：移除長條件運算對主體複雜度的影響 ───
const isWritableInput = (input: string, key: Key): boolean => {
  if (!input) return false;
  if (key.ctrl || key.meta) return false;

  const forbiddenInputs = ['\r', '\n', '\t', '\u001B[Z'];
  return !forbiddenInputs.includes(input);
};

export const ArgInput = ({ title, placeholder, usePlaceholderOnEmpty = true, onSubmit, onCancel }: ArgInputProps) => {
  const [inputValue, setInputValue] = useState('');
  const [cursorPosition, setCursorPosition] = useState(0);

  // ─── 💡 收攏光標與編輯邏輯：消除巢狀 if 的權重累加 ───
  const handleCursorAndEdit = (key: Key): boolean => {
    if (key.leftArrow) {
      setCursorPosition(previous => Math.max(0, previous - 1));
      return true;
    }

    if (key.rightArrow) {
      setCursorPosition(previous => Math.min(inputValue.length, previous + 1));
      return true;
    }

    if (key.home) {
      setCursorPosition(0);
      return true;
    }

    if (key.end) {
      setCursorPosition(inputValue.length);
      return true;
    }

    if (key.backspace) {
      if (cursorPosition > 0) {
        setInputValue(previous => previous.slice(0, cursorPosition - 1) + previous.slice(cursorPosition));
        setCursorPosition(previous => previous - 1);
      }

      return true;
    }

    if (key.delete) {
      if (cursorPosition < inputValue.length) {
        setInputValue(previous => previous.slice(0, cursorPosition) + previous.slice(cursorPosition + 1));
      }

      return true;
    }

    return false;
  };

  // 🎯 ─── 主入口流程：結構平鋪清晰，認知複雜度僅為 4 ───
  useInput((input, key) => {
    if (key.escape) {
      onCancel();
      return;
    }

    if (key.return) {
      const trimmed = inputValue.trim();
      onSubmit(trimmed.length === 0 && usePlaceholderOnEmpty ? placeholder : trimmed);
      return;
    }

    if (handleCursorAndEdit(key)) {
      return;
    }

    if (isWritableInput(input, key)) {
      setInputValue(previous => previous.slice(0, cursorPosition) + input + previous.slice(cursorPosition));
      setCursorPosition(previous => previous + input.length);
    }
  });

  const beforeCursor = inputValue.slice(0, cursorPosition);
  const atCursor = inputValue[cursorPosition] ?? ' ';
  const afterCursor = inputValue.slice(cursorPosition + 1);
  const hasInput = inputValue.length > 0;

  return (
    <Box flexDirection="column" paddingX={2} paddingTop={1}>
      <Text bold color="cyan">
        {title}
      </Text>

      {hasInput || cursorPosition > 0 ? (
        <Text color="white">
          {beforeCursor}
          <Text inverse color="white">
            {atCursor}
          </Text>
          {afterCursor}
        </Text>
      ) : (
        <Text color="gray" dimColor>
          {placeholder}
        </Text>
      )}

      <Box
        flexDirection="column"
        borderStyle="single"
        borderTop={true}
        borderBottom={false}
        borderLeft={false}
        borderRight={false}
        borderColor="gray"
        marginTop={0}
        paddingTop={0}
      >
        <Box justifyContent="space-between">
          <Text color="gray">enter confirm</Text>
          <Text color="gray">esc cancel</Text>
        </Box>
      </Box>
    </Box>
  );
};
