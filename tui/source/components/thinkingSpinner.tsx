import { useEffect, useState } from 'react';
import { Box, Text } from 'ink';

interface ThinkingSpinnerProps {
  message?: string;
  color?: string;
  intervalMs?: number;
}

const SPINNER_FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'];

export const ThinkingSpinner = ({
  message = 'Thinking...',
  color = 'gray',
  intervalMs = 100,
}: ThinkingSpinnerProps) => {
  const [frameIndex, setFrameIndex] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setFrameIndex(previous => (previous + 1) % SPINNER_FRAMES.length);
    }, intervalMs);

    return () => clearInterval(timer);
  }, [intervalMs]);

  const frame = SPINNER_FRAMES[frameIndex];

  return (
    <Box>
      <Text color={color}>{`${frame} ${message}`}</Text>
    </Box>
  );
};
