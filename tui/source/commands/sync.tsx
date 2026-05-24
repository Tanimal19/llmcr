import { useState, useEffect } from 'react';
import { Box, Text, useInput } from 'ink';

interface SyncCommandProps {
	onBack: () => void;
	targetPath?: string;
}

export const SyncCommand = ({ onBack, targetPath = 'C:/example_project/' }: SyncCommandProps) => {
	const [progress, setProgress] = useState(0);
	const [status, setStatus] = useState('正在初始化專案目錄結構...');

	useEffect(() => {
		const interval = setInterval(() => {
			setProgress(prev => {
				if (prev >= 100) {
					clearInterval(interval);
					setStatus('✨ 同步完成！所有動態 RAG 向量索引已成功更新。');
					return 100;
				}

				const next = prev + 5;
				// 🧠 依據進度百分比，動態切換嚴謹的工程狀態回饋
				if (next < 30) {
					setStatus('🔍 正在深度掃描專案原始碼 AST 節點...');
				} else if (next < 60) {
					setStatus('🧠 正在呼叫本地 LLM 生成程式碼語意向量嵌入 (Embeddings)...');
				} else if (next < 90) {
					setStatus('💾 正在將向量資料增量寫入本地輕量化資料庫...');
				}

				return next;
			});
		}, 80); // 每 80ms 刷新一次進度

		return () => clearInterval(interval);
	}, []);

	// 監聽鍵盤：只有在 100% 完成後，按下 Esc 才能安全退回主畫面
	useInput((_, key) => {
		if (key.escape && progress === 100) {
			onBack();
		}
	});

	// 動態渲染進度條字串 (總長度 20 格)
	const BAR_WIDTH = 20;
	const filledLength = Math.round((progress / 100) * BAR_WIDTH);
	const barString = '█'.repeat(filledLength) + '░'.repeat(BAR_WIDTH - filledLength);

	return (
		<Box flexDirection="column" paddingX={2} paddingTop={1}>
			{/* 標題欄 */}
			<Text bold color="yellow">🔄 Synchronizing Project Data</Text>
			<Text color="gray">
				目標目錄: <Text color="cyan" bold>{targetPath}</Text>
			</Text>

			{/* 💡 進度條本體 */}
			<Box flexDirection="row">
				<Text color="green">[{barString}] </Text>
				<Text bold color="green">{progress}%</Text>
			</Box>

			{/* 動態狀態提示 */}
			<Text color="white">{status}</Text>

			{/* 底部導覽：完成時才淡入 */}
			{progress === 100 && (
				<Box
					flexDirection="column"
					borderStyle="single"
					borderTop={true}
					borderBottom={false}
					borderLeft={false}
					borderRight={false}
					borderColor="gray"
					paddingTop={1}
					marginTop={1}
				>
					<Text color="gray">esc back to menu</Text>
				</Box>
			)}
		</Box>
	);
};
