import { useState } from 'react';
import { Box, Text, useInput } from 'ink';

type ArgInputProps = {
  title: string;
  placeholder: string;
  usePlaceholderOnEmpty?: boolean;
  onSubmit: (value: string) => void;
  onCancel: () => void;
};

export const ArgInput = ({ title, placeholder, usePlaceholderOnEmpty = true, onSubmit, onCancel }: ArgInputProps) => {
  const [inputValue, setInputValue] = useState('');
  const [cursorPosition, setCursorPosition] = useState(0);

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

    if (key.leftArrow) {
      setCursorPosition(previous => Math.max(0, previous - 1));
      return;
    }

    if (key.rightArrow) {
      setCursorPosition(previous => Math.min(inputValue.length, previous + 1));
      return;
    }

    if (key.backspace) {
      if (cursorPosition === 0) {
        return;
      }

      setInputValue(previous => previous.slice(0, cursorPosition - 1) + previous.slice(cursorPosition));
      setCursorPosition(previous => previous - 1);
      return;
    }

    if (key.delete) {
      if (cursorPosition >= inputValue.length) {
        return;
      }

      setInputValue(previous => previous.slice(0, cursorPosition) + previous.slice(cursorPosition + 1));
      return;
    }

    if (key.home) {
      setCursorPosition(0);
      return;
    }

    if (key.end) {
      setCursorPosition(inputValue.length);
      return;
    }

    if (input && !key.ctrl && !key.meta && input !== '\r' && input !== '\n' && input !== '\t' && input !== '\u001B[Z') {
      setInputValue(previous => previous.slice(0, cursorPosition) + input + previous.slice(cursorPosition));
      setCursorPosition(previous => previous + input.length);
    }
  });

  const beforeCursor = inputValue.slice(0, cursorPosition);
  const atCursor = inputValue[cursorPosition] || ' ';
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
