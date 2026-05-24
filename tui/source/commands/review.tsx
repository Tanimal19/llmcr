import { useState, useEffect } from 'react';
import { Box, Text, useInput } from 'ink';
import { CommandProps } from '../types.js'; // 引入我們定義的共同介面

interface ReviewCommandProps extends CommandProps {
	diffPath?: string;
}

export const ReviewCommand = ({ onBack, diffPath }: ReviewCommandProps) => {
	const [progress, setProgress] = useState(0);

    // 💡 新增：允許使用者在互動模式下，按 ESC 中斷任務並返回
	useInput((_, key) => {
		if (key.escape) {
			onBack();
		}
	});

	useEffect(() => {
		const timer = setInterval(() => {
			setProgress(prev => {
				if (prev >= 100) {
					clearInterval(timer);
					setTimeout(onBack, 500);
					return 100;
				}
				return prev + 10;
			});
		}, 100);

		return () => clearInterval(timer);
	}, [onBack]);

	const barWidth = 20;
	const completedWidth = Math.round((progress / 100) * barWidth);
	const progressBar = '█'.repeat(completedWidth) + '░'.repeat(barWidth - completedWidth);

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="magenta" bold>
				📊 正在執行 Review 進度審查...
			</Text>
			{diffPath && (
				<Text color="gray">Diff path: {diffPath}</Text>
			)}
			<Box marginTop={1}>
				<Text color="green">[{progressBar}] </Text>
				<Text>{progress}%</Text>
			</Box>
		</Box>
	);
};
