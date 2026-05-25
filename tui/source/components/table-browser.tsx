import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { Box, Text, useInput, type Key } from 'ink';
import { LoadingSpinner } from './loading-spinner.js';

const DEFAULT_PAGE_SIZE = 15;
const CURSOR_SYMBOL = '-> ';
const EMPTY_SYMBOL = '   ';
const CHECKED_SYMBOL = '● ';
const UNCHECKED_SYMBOL = '○ ';
const THEME_COLOR = 'cyan';

export type TableBrowserItem = {
  id: string;
  content: ReactNode;
  checked?: boolean;
};

type TableBrowserProps = {
  header?: ReactNode;
  items: TableBrowserItem[];
  footer?: ReactNode;
  showCheckbox?: boolean;
  loading?: boolean;
  loadingText?: string;
  enableInput?: boolean;
  onEscape?: () => void;
  onEnter?: (index: number) => void;
  onToggleCurrent?: (index: number) => void;
  onToggleAll?: () => void;
  onSwitchTable?: () => void;
  pageSize?: number;
};

export const TableBrowser = ({
  header,
  items,
  footer,
  showCheckbox = false,
  loading = false,
  loadingText = 'Loading...',
  enableInput = true,
  onEscape,
  onEnter,
  onToggleCurrent,
  onToggleAll,
  onSwitchTable,
  pageSize = DEFAULT_PAGE_SIZE,
}: TableBrowserProps) => {
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

  const handleArrowKeys = (key: Key): boolean => {
    if (key.upArrow) {
      setActiveIndex(previous => {
        const next = Math.max(0, previous - 1);
        if (next < windowStart) {
          setWindowStart(next);
        }

        return next;
      });
      return true;
    }

    if (key.downArrow) {
      setActiveIndex(previous => {
        const next = Math.min(items.length - 1, previous + 1);
        if (next >= windowStart + pageSize) {
          setWindowStart(next - pageSize + 1);
        }

        return next;
      });
      return true;
    }

    return false;
  };

  const handleCheckboxInput = (input: string): boolean => {
    if (!showCheckbox) {
      return false;
    }

    if (input === ' ') {
      onToggleCurrent?.(activeIndex);
      return true;
    }

    if (input === 'A') {
      onToggleAll?.();
      return true;
    }

    return false;
  };

  useInput((input, key) => {
    if (key.escape && onEscape) {
      onEscape?.();
      return;
    }

    if (!enableInput || loading) {
      return;
    }

    // '\u001B[Z' is the combination of Shift + Tab
    if ((key.tab && key.shift) || input === '\u001B[Z') {
      onSwitchTable?.();
      setActiveIndex(0);
      setWindowStart(0);
      return;
    }

    if (items.length === 0) {
      if (key.return) {
        onEnter?.(0);
      }

      return;
    }

    if (handleArrowKeys(key) || handleCheckboxInput(input)) {
      return;
    }

    if (key.return) {
      onEnter?.(activeIndex);
    }
  });

  return (
    <Box flexDirection="column" paddingX={1} paddingTop={1}>
      {header ? <Box flexDirection="column">{header}</Box> : null}

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
        {loading ? (
          <Box paddingLeft={1}>
            <LoadingSpinner message={loadingText} color="white" />
          </Box>
        ) : (
          visibleItems.map((item, visibleIndex) => {
            const absoluteIndex = windowStart + visibleIndex;
            const isCurrent = absoluteIndex === activeIndex;
            const cursorColor = isCurrent ? THEME_COLOR : 'gray';
            const checkboxPrefix = showCheckbox ? (item.checked ? CHECKED_SYMBOL : UNCHECKED_SYMBOL) : '';
            return (
              <Box key={item.id}>
                <Text color={cursorColor} bold={isCurrent}>
                  {isCurrent ? CURSOR_SYMBOL : EMPTY_SYMBOL}
                  {checkboxPrefix}
                </Text>
                <Box flexGrow={1}>{item.content}</Box>
              </Box>
            );
          })
        )}

        {!loading &&
          visibleItems.length < pageSize &&
          Array.from({ length: pageSize - visibleItems.length }).map((_, index) => (
            <Box key={`empty-${index}`} height={1} />
          ))}
      </Box>

      {footer ? (
        <Box flexDirection="column" marginTop={1}>
          {footer}
        </Box>
      ) : null}
    </Box>
  );
};
