import { useEffect, useState } from 'react';
import { Box, Text, useApp, useInput } from 'ink';
import { lsdb, type SourcePreview, type TrackRootPreview } from '../api.js';

const PAGE_SIZE = 10;

interface BrowserItem {
	id: string;
	label: string;
	checked: boolean;
	syncStatus: SourcePreview['syncStatus'];
}

function toLabel(path: string): string {
	const segments = path.split(/[/\\]/);
	return segments.at(-1) ?? path;
}

interface DbBrowserProps {
	editMode: boolean;
	onBack: () => void;
	oneShotArgs?: boolean;
}

export const DbBrowser = ({ editMode, onBack, oneShotArgs }: DbBrowserProps) => {
	const { exit } = useApp();
	const isOneShot = oneShotArgs === true;
	const [trackRoots, setTrackRoots] = useState<TrackRootPreview[]>([]);
	const [isLoading, setIsLoading] = useState(true);
	const [errorMsg, setErrorMsg] = useState<string | undefined>(undefined);

	useEffect(() => {
		let alive = true;

		(async () => {
			try {
				const previews = await lsdb();
				if (!alive) {
					return;
				}

				setTrackRoots(previews);
				setDbData(() => {
					const next: Record<string, BrowserItem[]> = {};
					for (const preview of previews) {
						next[preview.path] = preview.sources.map(source => ({
							id: String(source.id ?? source.path),
							label: toLabel(source.path),
							checked: source.syncStatus === 'SYNCED',
							syncStatus: source.syncStatus,
						}));
					}
					return next;
				});
			} catch (error) {
				if (!alive) {
					return;
				}
				const message = error instanceof Error ? error.message : String(error);
				setErrorMsg(message);
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

	const tableKeys = trackRoots.map(trackRoot => trackRoot.path);
	const [tableIndex, setTableIndex] = useState(0);
	const safeTableIndex = tableKeys.length === 0 ? 0 : Math.min(tableIndex, tableKeys.length - 1);
	const currentTable = tableKeys[safeTableIndex];

	const [dbData, setDbData] = useState<Record<string, BrowserItem[]>>({});

	// --- 2. 視窗滾動狀態（切換 table 時需重設） ---
	const [activeIndex, setActiveIndex] = useState(0);
	const [windowStart, setWindowStart] = useState(0);
	const [statusMsg, setStatusMsg] = useState('');

	// 取得當前 Table 的檔案清單
	const items = currentTable ? (dbData[currentTable] ?? []) : [];
	const windowEnd = windowStart + PAGE_SIZE;
	const visibleItems = items.slice(windowStart, windowEnd);
	const currentTrackRoot = currentTable ? trackRoots.find(trackRoot => trackRoot.path === currentTable) : undefined;

	// --- 3. 高階按鍵邏輯監聽 ---
	useInput((input, key) => {
		if (isLoading) {
			if (key.escape) {
				if (isOneShot) exit(); else onBack();
			}
			return;
		}

		if (errorMsg) {
			if (key.escape || key.return) {
				if (isOneShot) exit(); else onBack();
			}
			return;
		}

		if (statusMsg) return;

		// [通用] ESC 離開
		if (key.escape) {
			if (isOneShot) exit(); else onBack();
			return;
		}

		// [通用] ⬆️ 往上移
		if (key.upArrow) {
			if (items.length === 0) {
				return;
			}
			setActiveIndex(prev => {
				const next = Math.max(0, prev - 1);
				if (next < windowStart) setWindowStart(next);
				return next;
			});
		}

		// [通用] ⬇️ 往下移
		if (key.downArrow) {
			if (items.length === 0) {
				return;
			}
			setActiveIndex(prev => {
				const next = Math.min(items.length - 1, prev + 1);
				if (next >= windowStart + PAGE_SIZE) setWindowStart(next - PAGE_SIZE + 1);
				return next;
			});
		}

		// 💡 [通用] Shift + Tab 切換資料表
		// 在多數終端機環境中，Shift+Tab 會送出 'tab' 訊號且 key.shift 為 true，或是送出 '\u001b[Z' 逸出碼
		if ((key.tab && key.shift) || input === '\u001b[Z') {
			if (tableKeys.length === 0) {
				return;
			}
			setTableIndex(prev => (prev + 1) % tableKeys.length);
			setActiveIndex(0);    // 重設游標到新 Table 的第一行
			setWindowStart(0);   // 重設滑動視窗
			return;
		}

		// 💡 [編輯模式專屬] 空白鍵勾選 / 取消單項
		if (editMode && input === ' ') {
			if (!currentTable) {
				return;
			}
			setDbData(prev => ({
				...prev,
				[currentTable]: prev[currentTable]!.map((item, idx) =>
					idx === activeIndex ? { ...item, checked: !item.checked } : item
				),
			}));
		}

		// 💡 [編輯模式專屬] Shift + A 全選 / 全不選
		// 在終端機中，按住 Shift + a 會直接送出大寫的 'A'
		if (editMode && input === 'A') {
			if (!currentTable) {
				return;
			}
			const allChecked = items.every(item => item.checked);
			setDbData(prev => ({
				...prev,
				[currentTable]: prev[currentTable]!.map(item => ({
					...item,
					checked: !allChecked, // 如果本來全選就全取消，否則就全選
				})),
			}));
		}

		// [通用] Enter 送出或退出
		if (key.return) {
			if (editMode) {
				setStatusMsg(`已成功配置修訂！正在儲存並${isOneShot ? '退出' : '返回'}...`);
				setTimeout(isOneShot ? exit : onBack, 800);
			} else {
				if (isOneShot) exit(); else onBack();
			}
		}
	});

	// 樣式調整
	const themeColor = editMode ? 'cyan' : 'green';

	if (isLoading) {
		return (
			<Box flexDirection="column" paddingX={1} paddingTop={1} >
				<Text color="cyan" bold>Loading track roots...</Text>
				<Text color="gray">Press esc to {isOneShot ? 'exit' : 'back'}.</Text>
			</Box>
		);
	}

	if (errorMsg) {
		return (
			<Box flexDirection="column" paddingX={1} paddingTop={1} >
				<Text color="red" bold>Failed to load /lsdb</Text>
				<Text color="gray">{errorMsg}</Text>
				<Text color="gray">Press enter or esc to {isOneShot ? 'exit' : 'back'}.</Text>
			</Box>
		);
	}

	return (
		<Box flexDirection="column" paddingX={1} paddingTop={1} >
			<Text bold color={themeColor}>
				{currentTable ? `${currentTable}` : 'No track roots'}
			</Text>
			{currentTrackRoot && (
				<Text color={currentTrackRoot.isSynced ? 'green' : 'yellow'}>
					{currentTrackRoot.isSynced ? 'Synced' : 'Unsynced'}
				</Text>
			)}

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
				{visibleItems.map((item, visibleIdx) => {
					const absoluteIdx = windowStart + visibleIdx;
					const isCurrent = absoluteIdx === activeIndex;

					return (
						<Box key={item.id} justifyContent="space-between">
							<Box>
								<Text color={isCurrent ? themeColor : 'white'} bold={isCurrent}>
									{isCurrent ? '👉 ' : '   '}
									{editMode && (
										<Text color={item.checked ? themeColor : 'gray'}>
											{item.checked ? '▣ ' : '▢ '}
										</Text>
									)}
									{item.label}
								</Text>
							</Box>

							{item.syncStatus !== 'SYNCED' && (
								<Text color="yellow" dimColor>
									({item.syncStatus.toLowerCase()})
								</Text>
							)}
						</Box>
					);
				})}

				{visibleItems.length < PAGE_SIZE &&
					Array.from({ length: PAGE_SIZE - visibleItems.length }).map((_, i) => <Box key={i} height={1} />)
				}
			</Box>

			<Box flexDirection="column" marginTop={1}>
				<Box justifyContent="space-between">
					<Text color="gray">shift+tab switch track root</Text>
					<Text color="gray">⇅ scroll</Text>
				</Box>
				{editMode && (
					<Box flexDirection="column">
						<Box justifyContent="space-between">
							<Text color="gray">space     select/unselect</Text>
						</Box>
						<Box justifyContent="space-between">
							<Text color="gray">shift+A   select/unselect all</Text>
						</Box>
					</Box>
				)}
			</Box>

			{statusMsg ? (
				<Box marginTop={1}>
					<Text color="yellow" bold>{statusMsg}</Text>
				</Box>
			) : null}
		</Box>
	);
};
