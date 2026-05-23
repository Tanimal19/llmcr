import { useState, useEffect } from 'react';
import { Box, Text, useApp, useInput } from 'ink'; // 💡 1. 這裡加上了 useInput
import { TextInput } from '@inkjs/ui';
import { CommandProps } from '../types.js';

export const ChatCommand = ({ onBack, oneShotArgs }: CommandProps) => {
	const { exit } = useApp();
	const isOneShot = oneShotArgs !== undefined;

	// --- 狀態定義 ---
	// One-shot 模式用的答案狀態
	const [answer, setAnswer] = useState<string | null>(null);

	// 互動模式用的對話紀錄
	const [logs, setLogs] = useState<string[]>([
		'AI: 你好！輸入任何話我都會回應你，輸入 /exit 或按 [Esc] 即可回到主選單。'
	]);
	const [inputKey, setInputKey] = useState(0);

	// --- 💡 2. 新增：監聽鍵盤事件 ---
	useInput((_, key) => {
		// 如果不是單次執行模式，且使用者按下了 Escape 鍵
		if (!isOneShot && key.escape) {
			onBack(); // 直接中斷對話，退回主選單
		}
	});

	// --- 核心邏輯 1：單次執行 (One-shot) ---
	useEffect(() => {
		if (!isOneShot) return;

		const question = typeof oneShotArgs === 'string' ? oneShotArgs : '未輸入問題';
		const timer = setTimeout(() => {
			setAnswer(
				`🤖 LLM 單次回應：\n關於你問的「${question}」，我已經透過獨立模組分析完畢。這是一個標準的 One-shot 輸出！`
			);
		}, 1000); // 模擬思考 1 秒

		return () => clearTimeout(timer);
	}, [isOneShot, oneShotArgs]);

	// 答案產生後自動退出
	useEffect(() => {
		if (answer && isOneShot) {
			exit();
		}
	}, [answer, isOneShot, exit]);

	// --- 核心邏輯 2：互動模式 (Interactive) ---
	const handleInteractiveSubmit = (value: string) => {
		if (value.trim() === '/exit') {
			onBack();
			return;
		}
		if (value.trim() === '') return;

		setLogs(prev => [
			...prev,
			`You: ${value}`,
			`AI: 收到你的訊息了：「${value}」（模組化架構測試中）`
		]);
		setInputKey(prev => prev + 1); // 重新渲染 TextInput 藉此清空輸入框
	};

	// --- 畫面渲染分流 ---
	if (isOneShot) {
		if (!answer) {
			return (
				<Box padding={1}>
					<Text color="yellow" bold>⏳ 正在連線至 LLM 思考中... 請稍候...</Text>
				</Box>
			);
		}
		return (
			<Box flexDirection="column" padding={1}>
				<Text color="cyan" bold>⚡ [單次執行] 詢問：{oneShotArgs}</Text>
				<Box marginTop={1} paddingLeft={2}>
					<Text color="white">{answer}</Text>
				</Box>
			</Box>
		);
	}

	// 互動模式畫面
	return (
		<Box flexDirection="column" padding={1}>
			{/* 提示文字順便加上 [Esc] 提示，體驗更好 */}
			<Text color="yellow" bold>💬 對話模式 (輸入 /exit 或按 [Esc] 鍵返回主選單)</Text>
			<Box flexDirection="column" marginY={1} minHeight={5}>
				{logs.slice(-6).map((log, i) => (
					<Text key={i}>{log}</Text>
				))}
			</Box>
			<Box>
				<Text color="green">👉 &gt; </Text>
				<TextInput key={inputKey} placeholder="請輸入訊息..." onSubmit={handleInteractiveSubmit} />
			</Box>
		</Box>
	);
};
