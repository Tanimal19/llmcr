import { useState } from 'react';
import { Box, Text, useApp, useInput } from 'ink';

const PAGE_SIZE = 4;

// 💡 定義多個 Mock 資料庫資料表
const TABLES = ['JavaClass', 'PythonScripts', 'ConfigDB'];

interface DbBrowserProps {
	editMode: boolean;
	onBack: () => void;
	oneShotArgs?: boolean;
}

export const DbBrowser = ({ editMode, onBack, oneShotArgs }: DbBrowserProps) => {
	const { exit } = useApp();
	const isOneShot = oneShotArgs === true;

	// --- 1. 多資料表狀態管理 ---
	const [tableIndex, setTableIndex] = useState(0);
	const currentTable = TABLES[tableIndex]!;

	const [dbData, setDbData] = useState<Record<string, Array<{ id: string; label: string; checked: boolean; unsynced?: boolean }>>>({
		JavaClass: [
			{ id: 'j1', label: 'ClassNodeExtractor', checked: true },
			{ id: 'j2', label: 'DataSource', checked: false },
			{ id: 'j3', label: 'ClassNode', checked: true, unsynced: true },
			{ id: 'j4', label: 'BytecodeParser', checked: false },
			{ id: 'j5', label: 'SpringContextLoader', checked: false },
		],
		PythonScripts: [
			{ id: 'p1', label: 'data_processor.py', checked: true },
			{ id: 'p2', label: 'llm_client.py', checked: false },
			{ id: 'p3', label: 'embedder.py', checked: true, unsynced: true },
			{ id: 'p4', label: 'pipeline.py', checked: false },
		],
		ConfigDB: [
			{ id: 'c1', label: 'vector_settings.yaml', checked: false },
			{ id: 'c2', label: 'prompts.json', checked: true, unsynced: true },
		],
	});

	// --- 2. 視窗滾動狀態（切換 table 時需重設） ---
	const [activeIndex, setActiveIndex] = useState(0);
	const [windowStart, setWindowStart] = useState(0);
	const [statusMsg, setStatusMsg] = useState('');

	// 取得當前 Table 的檔案清單
	const items = dbData[currentTable] || [];
	const windowEnd = windowStart + PAGE_SIZE;
	const visibleItems = items.slice(windowStart, windowEnd);

	// --- 3. 高階按鍵邏輯監聽 ---
	useInput((input, key) => {
		if (statusMsg) return;

		// [通用] ESC 離開
		if (key.escape) {
			if (isOneShot) exit(); else onBack();
			return;
		}

		// [通用] ⬆️ 往上移
		if (key.upArrow) {
			setActiveIndex(prev => {
				const next = Math.max(0, prev - 1);
				if (next < windowStart) setWindowStart(next);
				return next;
			});
		}

		// [通用] ⬇️ 往下移
		if (key.downArrow) {
			setActiveIndex(prev => {
				const next = Math.min(items.length - 1, prev + 1);
				if (next >= windowStart + PAGE_SIZE) setWindowStart(next - PAGE_SIZE + 1);
				return next;
			});
		}

		// 💡 [通用] Shift + Tab 切換資料表
		// 在多數終端機環境中，Shift+Tab 會送出 'tab' 訊號且 key.shift 為 true，或是送出 '\u001b[Z' 逸出碼
		if ((key.tab && key.shift) || input === '\u001b[Z') {
			setTableIndex(prev => (prev + 1) % TABLES.length);
			setActiveIndex(0);    // 重設游標到新 Table 的第一行
			setWindowStart(0);   // 重設滑動視窗
			return;
		}

		// 💡 [編輯模式專屬] 空白鍵勾選 / 取消單項
		if (editMode && input === ' ') {
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

	return (
		<Box flexDirection="column" paddingX={1} paddingTop={1} width={50}>
			{/* 抬頭路徑 */}
			<Text bold color={themeColor}>
				{currentTable}/
			</Text>

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

							{item.unsynced && (
								<Text color="yellow" dimColor>
									(unsynced)
								</Text>
							)}
						</Box>
					);
				})}

				{visibleItems.length < PAGE_SIZE &&
					Array.from({ length: PAGE_SIZE - visibleItems.length }).map((_, i) => <Box key={i} height={1} />)
				}
			</Box>

			{/* 下方工具指南 Footer */}
			<Box flexDirection="column" marginTop={1}>
				<Box justifyContent="space-between">
					<Text color="gray">shift+tab switch table</Text>
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
