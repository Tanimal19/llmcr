import { useState } from 'react';
import { Box, Text, useInput, useApp } from 'ink';
// 匯入獨立的指令組件
import { ChatCommand } from './commands/chat.js';
import { ReviewCommand } from './commands/review.js';
import { SetRagCommand } from './commands/setrag.js';

// 定義 One-shot 旗標的型別
interface OneShotFlags {
	chat?: string;
	review?: boolean;
	setrag?: boolean;
}

// 主選單組件
const MainMenu = ({ onSelect }: { onSelect: (screen: string) => void }) => {
	const [activeIndex, setActiveIndex] = useState(0);
	const { exit } = useApp();
	const options = [
		{ label: '💬 Chat  - 模擬對話模式', value: 'chat' },
		{ label: '📊 Review - 跑進度條測試', value: 'review' },
		{ label: '⚙️ SetRAG - 配置文件清單', value: 'setrag' },
		{ label: '❌ Exit   - 退出程式', value: 'exit' }
	];

	useInput((_, key) => {
		if (key.upArrow) setActiveIndex(prev => Math.max(0, prev - 1));
		if (key.downArrow) setActiveIndex(prev => Math.min(options.length - 1, prev + 1));
		if (key.return) {
			const selected = options[activeIndex]?.value ?? 'exit';
			if (selected === 'exit') exit(); else onSelect(selected);
		}
	});

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="cyan" bold>=== 請選擇要執行的指令 (使用 ⬆️⬇️ 移動，Enter 確定) ===</Text>
			<Box flexDirection="column" marginTop={1}>
				{options.map((opt, idx) => (
					<Text key={opt.value} color={idx === activeIndex ? 'green' : 'white'}>
						{idx === activeIndex ? '👉 ' : '  '} {opt.label}
					</Text>
				))}
			</Box>
		</Box>
	);
};

// App 控制中心
export default function App({ oneShotFlags }: { oneShotFlags: OneShotFlags }) {
	// 🧠 核心路由邏輯：檢查 cli 是否有帶任何單次執行旗標
	const [currentScreen, setCurrentScreen] = useState<string>(() => {
		if (oneShotFlags.chat !== undefined) return 'chat_oneshot';
		if (oneShotFlags.review) return 'review_oneshot';
		if (oneShotFlags.setrag) return 'setrag_oneshot';
		return 'menu';
	});

	switch (currentScreen) {
		case 'menu':
			return <MainMenu onSelect={setCurrentScreen} />;

		// 💬 Chat 指令分流
		case 'chat_oneshot':
			return <ChatCommand onBack={() => setCurrentScreen('menu')} oneShotArgs={oneShotFlags.chat} />;
		case 'chat':
			return <ChatCommand onBack={() => setCurrentScreen('menu')} />;

		// 📊 Review 指令分流
		case 'review_oneshot':
			return <ReviewCommand onBack={() => setCurrentScreen('menu')} oneShotArgs={oneShotFlags.review} />;
		case 'review':
			return <ReviewCommand onBack={() => setCurrentScreen('menu')} />;

		// ⚙️ SetRAG 指令分流
		case 'setrag_oneshot':
			return <SetRagCommand onBack={() => setCurrentScreen('menu')} oneShotArgs={oneShotFlags.setrag} />;
		case 'setrag':
			return <SetRagCommand onBack={() => setCurrentScreen('menu')} />;

		default:
			return <Text>未知指令</Text>;
	}
}
