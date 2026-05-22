import { useState, useEffect } from 'react';
import { Box, Text, useInput, useApp } from 'ink';
import { TextInput } from '@inkjs/ui';

// ==========================================
// 1. 主畫面（指令選擇選單）
// ==========================================
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
		if (key.upArrow) {
			setActiveIndex(prev => Math.max(0, prev - 1));
		}
		if (key.downArrow) {
			setActiveIndex(prev => Math.min(options.length - 1, prev + 1));
		}
		if (key.return) {
			// 加上安全導航運算子 ?. 與預設值，解決 Object is possibly 'undefined'
			const selected = options[activeIndex]?.value ?? 'exit';
			if (selected === 'exit') {
				exit();
			} else {
				onSelect(selected);
			}
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

// ==========================================
// 2. Chat 指令（持續對話，直到輸入 /exit）
// ==========================================
const ChatScreen = ({ onBack }: { onBack: () => void }) => {
	const [logs, setLogs] = useState<string[]>(['AI: 你好！輸入任何話我都會回應你，輸入 /exit 即可回到主選單。']);
	// 因為 TextInput 在 @inkjs/ui 是非受控組件，我們用變更 key 的方式來強行清空輸入框
	const [inputKey, setInputKey] = useState(0);

	const handleSubmit = (value: string) => {
		if (value.trim() === '/exit') {
			onBack();
			return;
		}
		if (value.trim() === '') return;

		setLogs(prev => [
			...prev,
			`You: ${value}`,
			`AI: 收到你的訊息了：「${value}」（模擬回應）`
		]);

		// 觸發重新渲染 TextInput，達到清空效果
		setInputKey(prev => prev + 1);
	};

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="yellow" bold>💬 對話模式 (輸入 /exit 退出並返回主選單)</Text>
			<Box flexDirection="column" marginY={1} minHeight={5}>
				{logs.slice(-6).map((log, i) => (
					<Text key={i}>{log}</Text>
				))}
			</Box>
			<Box>
				<Text color="green">&gt; </Text>
				{/* 移除 value 和 onChange，改用 key 控制 */}
				<TextInput key={inputKey} placeholder="請輸入訊息..." onSubmit={handleSubmit} />
			</Box>
		</Box>
	);
};

// ==========================================
// 3. Review 指令（進度條跑完自動結束）
// ==========================================
const ReviewScreen = ({ onBack }: { onBack: () => void }) => {
	const [progress, setProgress] = useState(0);

	useEffect(() => {
		const timer = setInterval(() => {
			setProgress(prev => {
				if (prev >= 100) {
					clearInterval(timer);
					setTimeout(onBack, 500);
					return 100;
				}
				return prev + 4;
			});
		}, 80);

		return () => clearInterval(timer);
	}, [onBack]);

	const barWidth = 20;
	const completedWidth = Math.round((progress / 100) * barWidth);
	const progressBar = '█'.repeat(completedWidth) + '░'.repeat(barWidth - completedWidth);

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="magenta" bold>📊 正在執行 Review 進度審查...</Text>
			<Box marginTop={1}>
				<Text color="green">[{progressBar}] </Text>
				<Text>{progress}%</Text>
			</Box>
			{progress >= 100 && <Text color="gray">完成！正在返回主畫面...</Text>}
		</Box>
	);
};

// ==========================================
// 4. SetRAG 指令（空白鍵勾選，Enter 確定）
// ==========================================
const SetRagScreen = ({ onBack }: { onBack: () => void }) => {
	const [items, setItems] = useState([
		{ id: '1', label: '知識庫_產品說明書.pdf', checked: false },
		{ id: '2', label: '知識庫_公司常見問答.txt', checked: true },
		{ id: '3', label: '知識庫_2026財務報表.xlsx', checked: false },
	]);
	const [activeIndex, setActiveIndex] = useState(0);
	const [statusMsg, setStatusMsg] = useState('');

	useInput((input, key) => {
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
			setStatusMsg(`已成功配置 ${selectedLabels.length} 個檔案！正在返回...`);
			setTimeout(onBack, 1000);
		}
	});

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="blue" bold>⚙️ RAG 文件配置模式</Text>
			<Text color="gray">使用 ⬆️⬇️ 移動，[空白鍵] 勾選/取消，[Enter] 儲存並返回</Text>

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

// ==========================================
// 5. 總路由控制中心 (App Root)
// ==========================================
// 加上型別定義，允許接收腳手架傳進來的 name 屬性 (解決 Error 5)
export default function App({ name: _name }: { name?: string }) {
	const [currentScreen, setCurrentScreen] = useState('menu');

	switch (currentScreen) {
		case 'menu':
			return <MainMenu onSelect={setCurrentScreen} />;
		case 'chat':
			return <ChatScreen onBack={() => setCurrentScreen('menu')} />;
		case 'review':
			return <ReviewScreen onBack={() => setCurrentScreen('menu')} />;
		case 'setrag':
			return <SetRagScreen onBack={() => setCurrentScreen('menu')} />;
		default:
			return <Text>未知指令</Text>;
	}
}