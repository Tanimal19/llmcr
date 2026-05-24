import { useState } from 'react';
import { Box, Text, useApp, useInput } from 'ink';

const PAGE_SIZE = 4;

interface DbBrowserProps {
	editMode: boolean; // 💡 透過 mode 來決定是 setrag 還是 lsdb
	onBack: () => void;
	oneShotArgs?: boolean;
}

export const DbBrowser = ({ editMode, onBack, oneShotArgs }: DbBrowserProps) => {
	const { exit } = useApp();
	const isOneShot = oneShotArgs === true;

	// 統一集中的資料源
	const [items, setItems] = useState([
		{ id: '1', label: '知識庫_產品說明書.pdf', checked: false },
		{ id: '2', label: '知識庫_公司常見問答.txt', checked: true },
		{ id: '3', label: '知識庫_2026財務報表.xlsx', checked: false },
		{ id: '4', label: '核心演算法演練.md', checked: false },
		{ id: '5', label: '部署指南_Docker.yaml', checked: false },
		{ id: '6', label: 'API_V2_規格書.json', checked: true },
		{ id: '7', label: '環境變數範本.env', checked: false },
		{ id: '8', label: '客戶隱私條款_2026.docx', checked: false },
		{ id: '9', label: '測試測資數據_大模型.csv', checked: false },
		{ id: '10', label: 'README_開發必看.md', checked: false },
	]);

	// 滑動視窗核心狀態
	const [activeIndex, setActiveIndex] = useState(0);
	const [windowStart, setWindowStart] = useState(0);
	const [statusMsg, setStatusMsg] = useState('');

	const windowEnd = windowStart + PAGE_SIZE;
	const visibleItems = items.slice(windowStart, windowEnd);
	const remainingAbove = windowStart;
	const remainingBelow = Math.max(0, items.length - windowEnd);

	// 🎨 根據模式動態切換 UI 樣式主題
	const themeColor = editMode ? 'cyan' : 'green';
	const headerColor = editMode ? 'blue' : 'green';
	const titleText = editMode ? 'RAG 文件配置模式' : '當前知識庫檔案清單';
	const shortcutTag = isOneShot ? '⚡ [捷徑直達] ' : (editMode ? '⚙️ ' : '🔍 ');

	useInput((input, key) => {
		if (statusMsg) return;

		// 1. 共用鍵盤：ESC 放棄離開
		if (key.escape) {
			if (isOneShot) exit(); else onBack();
			return;
		}

		// 2. 共用鍵盤：上下移動與滾動算式
		if (key.downArrow) {
			setActiveIndex(prev => {
				const next = Math.min(items.length - 1, prev + 1);
				if (next >= windowStart + PAGE_SIZE) setWindowStart(next - PAGE_SIZE + 1);
				return next;
			});
		}
		if (key.upArrow) {
			setActiveIndex(prev => {
				const next = Math.max(0, prev - 1);
				if (next < windowStart) setWindowStart(next);
				return next;
			});
		}

		// 3. 變體鍵盤：僅在編輯模式下允許「空白鍵」勾選
		if (editMode && input === ' ') {
			setItems(prev => prev.map((item, idx) =>
				idx === activeIndex ? { ...item, checked: !item.checked } : item
			));
		}

		// 4. 變體鍵盤：Enter 送出處理
		if (key.return) {
			if (editMode) {
				const selectedLabels = items.filter(i => i.checked).map(i => i.label);
				setStatusMsg(`已變更設定！成功配置 ${selectedLabels.length} 個檔案，正在${isOneShot ? '關閉' : '返回'}...`);
				setTimeout(isOneShot ? exit : onBack, 1000);
			} else {
				// 唯讀模式直接秒退，不需提示
				if (isOneShot) exit(); else onBack();
			}
		}
	});

	return (
		<Box flexDirection="column" padding={1}>
			<Text color={headerColor} bold>
				{shortcutTag}{titleText}
				<Text color="gray"> ({activeIndex + 1}/{items.length})</Text>
			</Text>
			<Text color="gray">
				{editMode
					? '使用 ⬆️⬇️ 移動，[空白鍵] 勾選/取消，[Enter] 儲存修訂'
					: '使用 ⬆️⬇️ 瀏覽，按 [Enter] 或 [Esc] 退出檢視'
				}
			</Text>

			<Box flexDirection="column" marginY={1} borderStyle="round" borderColor={themeColor} paddingX={1}>
				{/* 頂部邊界 */}
				<Box height={1}>
					{remainingAbove > 0 ? (
						<Text color="gray" dimColor>▴ 上方還有 {remainingAbove} 個檔案...</Text>
					) : (
						<Text color="gray" dimColor>--- 列表頂端 ---</Text>
					)}
				</Box>

				{/* 核心列表渲染 */}
				<Box flexDirection="column" marginY={1}>
					{visibleItems.map((item, visibleIdx) => {
						const absoluteIdx = windowStart + visibleIdx;
						const isCurrent = absoluteIdx === activeIndex;
						const checkbox = item.checked ? '[𝘅]' : '[ ]';

						return (
							<Text key={item.id} color={isCurrent ? themeColor : 'white'} bold={isCurrent}>
								{isCurrent ? '👉 ' : '   '}
								{/* 💡 僅在編輯模式顯示勾選框 */}
								{editMode && <Text color={item.checked ? 'green' : 'gray'}>{checkbox} </Text>}
								{item.label}
							</Text>
						);
					})}
				</Box>

				{/* 底部邊界 */}
				<Box height={1}>
					{remainingBelow > 0 ? (
						<Text color="gray" dimColor>▾ 下方還有 {remainingBelow} 個檔案...</Text>
					) : (
						<Text color="gray" dimColor>--- 列表末端 ---</Text>
					)}
				</Box>
			</Box>

			{statusMsg ? <Text color="yellow">{statusMsg}</Text> : null}
		</Box>
	);
};
