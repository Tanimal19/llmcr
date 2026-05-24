import { useState } from 'react';
import { Box, Text, useApp, useInput } from 'ink';
import { CommandProps } from '../types.js';

const PAGE_SIZE = 4;

export const LsDbCommand = ({ onBack, oneShotArgs }: CommandProps) => {
	const { exit } = useApp();
	const isOneShot = oneShotArgs === true;

	// 沿用與 setrag 完全相同的靜態數據源（不需包含 checked，因為是唯讀）
	const [items] = useState([
		{ id: '1', label: '知識庫_產品說明書.pdf' },
		{ id: '2', label: '知識庫_公司常見問答.txt' },
		{ id: '3', label: '知識庫_2026財務報表.xlsx' },
		{ id: '4', label: '核心演算法演練.md' },
		{ id: '5', label: '部署指南_Docker.yaml' },
		{ id: '6', label: 'API_V2_規格書.json' },
		{ id: '7', label: '環境變數範本.env' },
		{ id: '8', label: '客戶隱私條款_2026.docx' },
		{ id: '9', label: '測試測資數據_大模型.csv' },
		{ id: '10', label: 'README_開發必看.md' },
	]);

	// 滑動視窗狀態
	const [activeIndex, setActiveIndex] = useState(0);
	const [windowStart, setWindowStart] = useState(0);

	const windowEnd = windowStart + PAGE_SIZE;
	const visibleItems = items.slice(windowStart, windowEnd);

	const remainingAbove = windowStart;
	const remainingBelow = items.length - windowEnd;

	// 監聽鍵盤
	useInput((_input, key) => {
		// 往下移動
		if (key.downArrow) {
			setActiveIndex(prev => {
				const next = Math.min(items.length - 1, prev + 1);
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
				if (next < windowStart) {
					setWindowStart(next);
				}
				return next;
			});
		}

		// 💡 使用者要求：按 Enter 或 ESC 都直接結束/返回
		if (key.return || key.escape) {
			if (isOneShot) {
				exit();
			} else {
				onBack();
			}
		}
	});

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="green" bold>
				{isOneShot ? '⚡ [捷徑直達] ' : '🔍 '}當前知識庫檔案清單
				<Text color="gray"> ({activeIndex + 1}/{items.length})</Text>
			</Text>
			<Text color="gray">
				使用 ⬆️⬇️ 瀏覽，按 <Text color="yellow">[Enter]</Text> 或 <Text color="yellow">[Esc]</Text> 退出檢視
			</Text>

			<Box flexDirection="column" marginY={1} borderStyle="round" borderColor="green" paddingX={1}>
				{/* 頂部邊界 */}
				<Box height={1}>
					{remainingAbove > 0 ? (
						<Text color="gray" dimColor>▴ 上方還有 {remainingAbove} 個檔案...</Text>
					) : (
						<Text color="gray" dimColor>--- 列表頂端 ---</Text>
					)}
				</Box>

				{/* 列表內容（無勾選框） */}
				<Box flexDirection="column" marginY={1}>
					{visibleItems.map((item, visibleIdx) => {
						const absoluteIdx = windowStart + visibleIdx;
						const isCurrent = absoluteIdx === activeIndex;

						return (
							<Text key={item.id} color={isCurrent ? 'green' : 'white'} bold={isCurrent}>
								{isCurrent ? '👉 ' : '   '} {item.label}
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
		</Box>
	);
};
