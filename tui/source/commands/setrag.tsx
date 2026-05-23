import { useState } from 'react';
import { Box, Text, useApp, useInput } from 'ink';
import { CommandProps } from '../types.js';

// 設定可視範圍的大小（滑動視窗高度）
const PAGE_SIZE = 5;

export const SetRagCommand = ({ onBack, oneShotArgs }: CommandProps) => {
	const { exit } = useApp();
	const isOneShot = oneShotArgs === true;

	// --- 1. 擴充模擬數據，方便測試滾動效果 ---
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

	// --- 2. 視窗滾動核心狀態 ---
	const [activeIndex, setActiveIndex] = useState(0);
	const [windowStart, setWindowStart] = useState(0); // 目前視窗從哪一個 Index 開始顯示
	const [statusMsg, setStatusMsg] = useState('');

	// 計算當前視窗結束的 Index
	const windowEnd = windowStart + PAGE_SIZE;
	// 擷取當前要顯示的子陣列（滑動視窗範圍）
	const visibleItems = items.slice(windowStart, windowEnd);

	// 計算上方與下方各別還埋了多少個檔案未顯示
	const remainingAbove = windowStart;
	const remainingBelow = items.length - windowEnd;

	// --- 3. 滾動演算法按鍵監聽 ---
	useInput((input, key) => {
        if (statusMsg) return;

        // 💡 新增：在選單選到一半時，按 ESC 鍵可以直接放棄並返回主選單
        if (!isOneShot && key.escape) {
            onBack();
            return;
        }

		// 往下移動
		if (key.downArrow) {
			setActiveIndex(prev => {
				const next = Math.min(items.length - 1, prev + 1);
				// 🧠 滑動視窗邏輯：如果下一行超出了目前可視窗的底部，視窗往下滾一格
				if (next >= windowStart + PAGE_SIZE) {
					setWindowStart(next - PAGE_SIZE + 1);
				}
				return next;
			});
		}

		// 往上移動
		if (key.upArrow) {
			setActiveIndex(prev => {
				const next = Math.max(0, prev - 1);
				// 🧠 滑動視窗邏輯：如果上一行高於目前可視窗的頂部，視窗往上滾一格
				if (next < windowStart) {
					setWindowStart(next);
				}
				return next;
			});
		}

		// 空白鍵勾選
		if (input === ' ') {
			setItems(prev => prev.map((item, idx) =>
				idx === activeIndex ? { ...item, checked: !item.checked } : item
			));
		}

		// Enter 送出
		if (key.return) {
			const selectedLabels = items.filter(i => i.checked).map(i => i.label);
			if (isOneShot) {
				setStatusMsg(`已變更設定！成功配置 ${selectedLabels.length} 個檔案，正在關閉...`);
				setTimeout(exit, 1000);
			} else {
				setStatusMsg(`已變更設定！成功配置 ${selectedLabels.length} 個檔案，正在返回...`);
				setTimeout(onBack, 1000);
			}
		}
	});

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="blue" bold>
				{isOneShot ? '⚡ [捷徑直達] ' : '⚙️ '}RAG 文件配置模式
				<Text color="gray"> ({activeIndex + 1}/{items.length})</Text>
			</Text>
			<Text color="gray">
				使用 ⬆️⬇️ 移動，[空白鍵] 勾選/取消，[Enter] 儲存
			</Text>

			{/* 顯示選單核心區域 */}
			<Box flexDirection="column" marginY={1} borderStyle="round" borderColor="gray" paddingX={1}>

				{/* 頂部邊界提示：如果上面還有檔案，印出 ▴ 提示 */}
				<Box height={1}>
					{remainingAbove > 0 ? (
						<Text color="yellow" dimColor>▴ 還有 {remainingAbove} 個檔案...</Text>
					) : (
						<Text color="gray" dimColor>--- 列表頂端 ---</Text>
					)}
				</Box>

				{/* 渲染滑動視窗內部的檔案 */}
				<Box flexDirection="column" marginY={1}>
					{visibleItems.map((item, visibleIdx) => {
						// 💡 注意：因為 items 被 slice 了，當前的絕對 index 必須加上 windowStart
						const absoluteIdx = windowStart + visibleIdx;
						const isCurrent = absoluteIdx === activeIndex;
						const checkbox = item.checked ? '[𝘅]' : '[ ]';

						return (
							<Text key={item.id} color={isCurrent ? 'cyan' : 'white'}>
								{isCurrent ? '👉 ' : '   '}
								<Text color={item.checked ? 'green' : 'gray'}>{checkbox}</Text> {item.label}
							</Text>
						);
					})}
				</Box>

				{/* 底部邊界提示：如果下面還有檔案，印出 ▾ 提示 */}
				<Box height={1}>
					{remainingBelow > 0 ? (
						<Text color="yellow" dimColor>▾ 還有 {remainingBelow} 個檔案...</Text>
					) : (
						<Text color="gray" dimColor>--- 列表末端 ---</Text>
					)}
				</Box>
			</Box>

			{statusMsg ? <Text color="yellow">{statusMsg}</Text> : null}
		</Box>
	);
};
