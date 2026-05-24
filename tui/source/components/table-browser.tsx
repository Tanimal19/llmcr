import { useEffect, useMemo, useState } from 'react';
import { Box, Text, useInput } from 'ink';

const DEFAULT_PAGE_SIZE = 15;
const CURSOR_SYMBOL = '-> ';
const EMPTY_SYMBOL = '   ';
const CHECKED_SYMBOL = '● ';
const UNCHECKED_SYMBOL = '○ ';
const THEME_COLOR = 'cyan';

export type TableBrowserItem = {
  id: string;
  label: string;
  checked?: boolean;
  rightText?: string;
};

type TableBrowserProps = {
  title: string;
  subtitle?: string;
  items: TableBrowserItem[];
  showCheckbox?: boolean;
  loading: boolean;
  loadingText: string;
  errorText?: string;
  errorEnterAction?: 'escape' | 'clear';
  statusText?: string;
  escapeHint: string;
  leftHelpLines?: string[];
  rightHelpLines?: string[];
  onEscape: () => void;
  onEnter?: () => void;
  onToggleCurrent?: (index: number) => void;
  onToggleAll?: () => void;
  onSwitchTable?: () => void;
  onClearError?: () => void;
};

export const TableBrowser = ({
  title,
  subtitle,
  items,
  showCheckbox = false,
  loading,
  loadingText,
  errorText,
  errorEnterAction = 'escape',
  statusText,
  escapeHint,
  leftHelpLines = [],
  rightHelpLines = [],
  onEscape,
  onEnter,
  onToggleCurrent,
  onToggleAll,
  onSwitchTable,
  onClearError,
}: TableBrowserProps) => {
  const pageSize = DEFAULT_PAGE_SIZE;
  const [activeIndex, setActiveIndex] = useState(0);
  const [windowStart, setWindowStart] = useState(0);

  useEffect(() => {
    if (items.length === 0) {
      setActiveIndex(0);
      setWindowStart(0);
      return;
    }

    setActiveIndex(previous => Math.min(previous, items.length - 1));
    setWindowStart(previous => {
      const maxStart = Math.max(0, items.length - pageSize);
      return Math.min(previous, maxStart);
    });
  }, [items.length, pageSize]);

  const windowEnd = windowStart + pageSize;
  const visibleItems = useMemo(() => items.slice(windowStart, windowEnd), [items, windowStart, windowEnd]);
  const helpRowCount = Math.max(leftHelpLines.length, rightHelpLines.length);

  useInput((input, key) => {
    if (key.escape) {
      onEscape();
      return;
    }

    if (loading || statusText) {
      return;
    }

    if (errorText) {
      if (key.return) {
        if (errorEnterAction === 'clear') {
          onClearError?.();
          return;
        }

        onEscape();
      }

      return;
    }

    if ((key.tab && key.shift) || input === '\u001B[Z') {
      onSwitchTable?.();
      setActiveIndex(0);
      setWindowStart(0);
      return;
    }

    if (items.length === 0) {
      if (key.return) {
        onEnter?.();
      }

      return;
    }

    if (key.upArrow) {
      setActiveIndex(previous => {
        const next = Math.max(0, previous - 1);
        if (next < windowStart) {
          setWindowStart(next);
        }

        return next;
      });
      return;
    }

    if (key.downArrow) {
      setActiveIndex(previous => {
        const next = Math.min(items.length - 1, previous + 1);
        if (next >= windowStart + pageSize) {
          setWindowStart(next - pageSize + 1);
        }

        return next;
      });
      return;
    }

    if (showCheckbox && input === ' ') {
      onToggleCurrent?.(activeIndex);
      return;
    }

    if (showCheckbox && input === 'A') {
      onToggleAll?.();
      return;
    }

    if (key.return) {
      onEnter?.();
    }
  });

  if (loading) {
    return (
      <Box flexDirection="column" paddingX={1} paddingTop={1}>
        <Text color="white" bold>
          {loadingText}
        </Text>
        <Text color="gray">Press esc to {escapeHint}.</Text>
      </Box>
    );
  }

  if (errorText && errorEnterAction === 'escape') {
    return (
      <Box flexDirection="column" paddingX={1} paddingTop={1}>
        <Text color="red" bold>
          Failed to load data
        </Text>
        <Text color="gray">{errorText}</Text>
        <Text color="gray">Press enter or esc to {escapeHint}.</Text>
      </Box>
    );
  }

  return (
    <Box flexDirection="column" paddingX={1} paddingTop={1}>
      <Text color="white" bold>
        {title}
      </Text>
      {subtitle ? <Text color="gray">{subtitle}</Text> : null}

      <Box
        flexDirection="column"
        borderStyle="single"
        borderTop={true}
        borderBottom={true}
        borderLeft={false}
        borderRight={false}
        borderColor="gray"
        paddingY={0}
        marginY={0}
      >
        {visibleItems.map((item, visibleIndex) => {
          const absoluteIndex = windowStart + visibleIndex;
          const isCurrent = absoluteIndex === activeIndex;
          const rowColor = isCurrent ? THEME_COLOR : showCheckbox ? (item.checked ? 'white' : 'gray') : 'white';
          const checkboxPrefix = showCheckbox ? (item.checked ? CHECKED_SYMBOL : UNCHECKED_SYMBOL) : '';
          const rightText = item.rightText ? ` ${item.rightText}` : '';
          return (
            <Box key={item.id}>
              <Text color={rowColor} bold={isCurrent}>
                {isCurrent ? CURSOR_SYMBOL : EMPTY_SYMBOL}
                {checkboxPrefix}
                {item.label}
                {rightText}
              </Text>
            </Box>
          );
        })}

        {visibleItems.length < pageSize &&
          Array.from({ length: pageSize - visibleItems.length }).map((_, index) => (
            <Box key={`empty-${index}`} height={1} />
          ))}
      </Box>

      <Box flexDirection="column" marginTop={1}>
        {Array.from({ length: helpRowCount }).map((_, index) => (
          <Box key={`help-${index}`} justifyContent="space-between">
            <Text color="gray">{leftHelpLines[index] ?? ''}</Text>
            <Text color="gray">{rightHelpLines[index] ?? ''}</Text>
          </Box>
        ))}
      </Box>

      {errorText ? (
        <Box marginTop={1}>
          <Text color="red" bold>
            {errorText}
          </Text>
        </Box>
      ) : null}

      {statusText ? (
        <Box marginTop={1}>
          <Text color="green" bold>
            {statusText}
          </Text>
        </Box>
      ) : null}
    </Box>
  );
};
