import { useEffect, useMemo, useState } from 'react';
import { Box, Text } from 'ink';
import { TableBrowser, type TableBrowserItem, toLabel } from './components/table-browser.js';
import { getRagScope, setRagScope } from '#api.js';
import { type CommandProps } from '#screens/types.js';
import { LoadingSpinner } from '#components/loading-spinner.js';

type RagScopeItem = {
  id: string;
  path: string;
  label: string;
  checked: boolean;
};

const LABEL_COLUMN_WIDTH = 24;
const PATH_COLUMN_WIDTH = 56;

export const SetRagCommand = ({ onBack }: CommandProps) => {
  const [items, setItems] = useState<RagScopeItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | undefined>(undefined);
  const [statusMsg, setStatusMsg] = useState<string | undefined>(undefined);

  const selectedCount = useMemo(() => items.filter(item => item.checked).length, [items]);

  const leave = () => {
    onBack();
  };

  useEffect(() => {
    let alive = true;

    (async () => {
      try {
        const scopeMap = await getRagScope();
        if (!alive) {
          return;
        }

        const nextItems = Object.entries(scopeMap)
          .map(([path, checked]) => ({
            id: path,
            path,
            label: toLabel(path),
            checked,
          }))
          .toSorted((a, b) => a.id.localeCompare(b.id));

        setItems(nextItems);
      } catch (error) {
        if (!alive) {
          return;
        }

        setErrorMsg(error instanceof Error ? error.message : String(error));
      } finally {
        if (alive) {
          setIsLoading(false);
        }
      }
    })();

    return () => {
      alive = false;
    };
  }, []);

  const saveScope = async () => {
    if (isSaving) {
      return;
    }

    const selectedPaths = items.filter(item => item.checked).map(item => item.id);
    if (selectedPaths.length === 0) {
      setErrorMsg('Please select at least one track root.');
      return;
    }

    setIsSaving(true);
    setErrorMsg(undefined);
    setStatusMsg('Modifying rag scope...');

    try {
      await setRagScope(selectedPaths);
      setStatusMsg(`Complete. Going back...`);
      setTimeout(leave, 700);
    } catch (error) {
      setStatusMsg(undefined);
      setErrorMsg(error instanceof Error ? error.message : String(error));
    } finally {
      setIsSaving(false);
    }
  };

  const tableItems = useMemo<TableBrowserItem[]>(
    () =>
      items.map(item => ({
        id: item.id,
        checked: item.checked,
        content: (
          <Box>
            <Box width={LABEL_COLUMN_WIDTH} marginRight={1}>
              <Text color={item.checked ? 'white' : 'gray'} wrap="truncate-end">
                {item.label}
              </Text>
            </Box>
            <Box width={PATH_COLUMN_WIDTH}>
              <Text color="gray" wrap="truncate-end">
                {item.path}
              </Text>
            </Box>
          </Box>
        ),
      })),
    [items],
  );

  return (
    <TableBrowser
      header={
        <>
          <Text color="white" bold>
            RAG Scope
          </Text>
          <Text color="gray">{`Selected: ${selectedCount}/${items.length}`}</Text>
        </>
      }
      items={tableItems}
      footer={
        <>
          <Box justifyContent="space-between">
            <Text color="gray">up/down move</Text>
            <Text color="gray">enter save</Text>
          </Box>
          <Box justifyContent="space-between">
            <Text color="gray">space toggle</Text>
            <Text color="gray">esc back</Text>
          </Box>
          <Box justifyContent="space-between">
            <Text color="gray">shift+A toggle all</Text>
            <Text color="gray" />
          </Box>
          {isSaving && statusMsg ? <LoadingSpinner message={statusMsg} color="green" /> : null}
          {!isSaving && statusMsg ? (
            <Text color="green" bold>
              {statusMsg}
            </Text>
          ) : null}
          {errorMsg ? (
            <Text color="red" bold>
              {errorMsg}
            </Text>
          ) : null}
        </>
      }
      showCheckbox={true}
      loading={isLoading}
      loadingText="Loading RAG scope..."
      enableInput={!isSaving}
      onEscape={leave}
      onEnter={() => {
        if (errorMsg) {
          setErrorMsg(undefined);
          return;
        }

        void saveScope();
      }}
      onToggleCurrent={index => {
        setItems(previous =>
          previous.map((item, itemIndex) => (itemIndex === index ? { ...item, checked: !item.checked } : item)),
        );
      }}
      onToggleAll={() => {
        const isAllChecked = items.every(item => item.checked);
        setItems(previous =>
          previous.map(item => ({
            ...item,
            checked: !isAllChecked,
          })),
        );
      }}
    />
  );
};
