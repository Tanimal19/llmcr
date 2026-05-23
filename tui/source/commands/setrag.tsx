import { useState } from 'react';
import { Box, Text, useApp, useInput } from 'ink';
import { CommandProps } from '../types.js';

export const SetRagCommand = ({ onBack, oneShotArgs }: CommandProps) => {
	const { exit } = useApp();
	// 💡 因為在 cli.tsx 改成了 boolean，這裡直接判斷是否為 true
	const isOneShot = oneShotArgs === true;

	// --- 狀態定義（兩模式共用） ---
	const [items, setItems] = useState([
		{ id: '1', label: '知識庫_產品說明書.pdf', checked: false },
		{ id: '2', label: '知識庫_公司常見問答.txt', checked: true },
		{ id: '3', label: '知識庫_2026財務報表.xlsx', checked: false },
	]);
	const [activeIndex, setActiveIndex] = useState(0);
	const [statusMsg, setStatusMsg] = useState('');

	// --- 監聽按鍵（兩模式共用） ---
	useInput((input, key) => {
		if (statusMsg) return; // 如果正在讀秒退出，鎖定按鍵不讓使用者亂動

		if (key.upArrow) {
			setActiveIndex(prev => Math.max(0, prev - 1));
		}
		if (key.downArrow) {
			setActiveIndex(prev => Math.min(items.length - 1, prev + 1));
		}
		if (input === ' ') {
			setItems(prev => prev.map((item, idx) =>
				idx === activeIndex ? { ...item, checked: !item.checked } : item
			));
		}
		if (key.return) {
			const selectedLabels = items.filter(i => i.checked).map(i => i.label);

			// 💡 關鍵分流點：根據進入管道決定結束後的去處
			if (isOneShot) {
				setStatusMsg(`已成功配置 ${selectedLabels.length} 個檔案！正在同步向量庫並退出...`);
				setTimeout(exit, 1000); // 捷徑模式：直接關閉整個 TUI 程式
			} else {
				setStatusMsg(`已成功配置 ${selectedLabels.length} 個檔案！正在返回主選單...`);
				setTimeout(onBack, 1000); // 選單模式：退回上一頁
			}
		}
	});

	return (
		<Box flexDirection="column" padding={1}>
			{/* 提示小標頭優化 */}
			<Text color="blue" bold>
				{isOneShot ? '⚡ [捷徑直達] ' : '⚙️ '}RAG 文件配置模式
			</Text>
			<Text color="gray">
				使用 ⬆️⬇️ 移動，[空白鍵] 勾選/取消，[Enter] 儲存並{isOneShot ? '退出' : '返回'}
			</Text>

			<Box flexDirection="column" marginY={1}>
				{items.map((item, idx) => {
					const isCurrent = idx === activeIndex;
					const checkbox = item.checked ? '[𝘅]' : '[ ]';
					return (
						<Text key={item.id} color={isCurrent ? 'cyan' : 'white'}>
							{isCurrent ? '👉 ' : '   '}
							<Text color={item.checked ? 'green' : 'gray'}>{checkbox}</Text> {item.label}
						</Text>
					);
				})}
			</Box>
			{statusMsg ? <Text color="yellow">{statusMsg}</Text> : null}
		</Box>
	);
};
