import { useState } from 'react';
import { Box, Text, useInput, useApp } from 'ink';
import { ChatCommand } from './commands/chat.js';
import { ReviewCommand } from './commands/review.js';
import { SetRagCommand } from './commands/setrag.js';
import { LsDbCommand } from './commands/lsdb.js';

interface OneShotFlags {
	chat?: string;
	review?: boolean;
	setrag?: boolean;
	lsdb?: boolean;
}

const MainMenu = ({ onSelect }: { onSelect: (screen: string) => void }) => {
	const [activeIndex, setActiveIndex] = useState(0);
	const { exit } = useApp();
	const options = [
		{ label: '💬 Chat  - 模擬對話模式', value: 'chat' },
		{ label: '📊 Review - 跑進度條測試', value: 'review' },
		{ label: '⚙️ SetRAG - 配置文件清單', value: 'setrag' },
		{ label: '🔍 LsDB   - 查看目前知識庫列表', value: 'lsdb' },
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

export default function App({ oneShotFlags }: { oneShotFlags: OneShotFlags }) {
	// 🧠 核心路由邏輯：檢查 cli 是否有帶任何單次執行旗標
	const [currentScreen, setCurrentScreen] = useState<string>(() => {
		if (oneShotFlags.chat !== undefined) return 'chat_oneshot';
		if (oneShotFlags.review) return 'review_oneshot';
		if (oneShotFlags.setrag) return 'setrag_oneshot';
        if (oneShotFlags.lsdb) return 'lsdb_oneshot';
		return 'menu';
	});

	switch (currentScreen) {
		case 'menu':
			return <MainMenu onSelect={setCurrentScreen} />;
		case 'chat_oneshot':
			return <ChatCommand onBack={() => setCurrentScreen('menu')} oneShotArgs={oneShotFlags.chat} />;
		case 'chat':
			return <ChatCommand onBack={() => setCurrentScreen('menu')} />;
		case 'review_oneshot':
			return <ReviewCommand onBack={() => setCurrentScreen('menu')} oneShotArgs={true} />;
		case 'review':
			return <ReviewCommand onBack={() => setCurrentScreen('menu')} />;
		case 'setrag_oneshot':
			return <SetRagCommand onBack={() => setCurrentScreen('menu')} oneShotArgs={true} />;
		case 'setrag':
			return <SetRagCommand onBack={() => setCurrentScreen('menu')} />;
		case 'lsdb_oneshot':
			return <LsDbCommand onBack={() => setCurrentScreen('menu')} oneShotArgs={true} />;
		case 'lsdb':
			return <LsDbCommand onBack={() => setCurrentScreen('menu')} />;

		default:
			return <Text>未知指令</Text>;
	}
}
