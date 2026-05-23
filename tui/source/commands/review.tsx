import { useState, useEffect } from 'react';
import { Box, Text, useApp } from 'ink';
import { CommandProps } from '../types.js'; // 引入我們定義的共同介面

export const ReviewCommand = ({ onBack, oneShotArgs }: CommandProps) => {
	const [progress, setProgress] = useState(0);
	const { exit } = useApp();
	const isOneShot = oneShotArgs !== undefined;

	useEffect(() => {
		const timer = setInterval(() => {
			setProgress(prev => {
				if (prev >= 100) {
					clearInterval(timer);

					// 關鍵路由決定：單次執行就直接關閉程式，互動選單就回上一頁
					if (isOneShot) {
						setTimeout(exit, 200);
					} else {
						setTimeout(onBack, 500);
					}
					return 100;
				}
				return prev + 10;
			});
		}, 100);

		return () => clearInterval(timer);
	}, [onBack, exit, isOneShot]);

	const barWidth = 20;
	const completedWidth = Math.round((progress / 100) * barWidth);
	const progressBar = '█'.repeat(completedWidth) + '░'.repeat(barWidth - completedWidth);

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="magenta" bold>
				{isOneShot ? '⚡ [單次執行] ' : '📊 '}正在執行 Review 進度審查...
			</Text>
			<Box marginTop={1}>
				<Text color="green">[{progressBar}] </Text>
				<Text>{progress}%</Text>
			</Box>
		</Box>
	);
};
