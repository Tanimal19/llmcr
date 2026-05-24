import { useState } from 'react';
import { Box, Text, useInput } from 'ink';

interface ArgInputProps {
  title: string;
  placeholder: string;
  usePlaceholderOnEmpty?: boolean;
  onSubmit: (value: string) => void;
  onCancel: () => void;
}

export const ArgInput = ({ title, placeholder, usePlaceholderOnEmpty = true, onSubmit, onCancel }: ArgInputProps) => {
  const [inputValue, setInputValue] = useState('');

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
      <Text bold color="cyan">📝 {title}</Text>

      <Box borderStyle="round" borderColor="green" paddingX={1} marginY={1}>
        {inputValue ? (
          <Text color="white" bold>{inputValue}<Text color="green">┃</Text></Text>
        ) : (
          <Text color="gray" dimColor>{placeholder}</Text>
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