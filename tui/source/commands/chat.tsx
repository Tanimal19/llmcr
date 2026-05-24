import { useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { TextInput } from '@inkjs/ui';
import { CommandProps } from '../types.js';
import { chat, type ChatResponse } from "../api.js";

// 💡 擴充 role 型態：加入 'system_reply' 用來渲染不需要帶 >>> 前綴的系統指令提示
interface Message {
	role: 'user' | 'assistant' | 'system_reply';
	text: string;
}

export const ChatCommand = ({ onBack, oneShotArgs }: CommandProps) => {
	const { exit } = useApp();
	const isOneShot = oneShotArgs !== undefined;

	// ─── 狀態管理 ───
	const [messages, setMessages] = useState<Message[]>([]);
	const [inputKey, setInputKey] = useState(0);
	const [isLoading, setIsLoading] = useState(false);

	// 監聽 Esc 鍵隨時返回主選單
	useInput((_, key) => {
		if (key.escape) {
			onBack();
		}
	});

	// ─── 流程 1：單次快捷模式 (One-shot Mode) ───
	useEffect(() => {
		if (!isOneShot) return;

		const executeOneShot = async () => {
			setIsLoading(true);
			try {
				const res = await chat(String(oneShotArgs));
				setOneShotAnswer(res.answer);
			} catch {
				setOneShotAnswer("❌ 錯誤：無法完成單次 API 請求。");
			} finally {
				setIsLoading(false);
			}
		};
		executeOneShot();
	}, [isOneShot, oneShotArgs]);

	useEffect(() => {
		if (oneShotAnswer && isOneShot) {
			exit();
		}
	}, [oneShotAnswer, isOneShot, exit]);

	// ─── 流程 2：互動對話模式 (精準模擬 Ollama 核心內建指令機制) ───
	const handleInteractiveSubmit = async (value: string) => {
		const trimmed = value.trim();

		// 🎯 規則 1：如果是空的直接按 Enter
		// 在歷史紀錄追加一筆空的 user 訊息，重置輸入框，畫面上就會自然多出一個緊湊的 >>> 換行
		if (value === '') {
			setMessages(prev => [...prev, { role: 'user', text: '' }]);
			setInputKey(prev => prev + 1);
			return;
		}

		// 🎯 規則 2：如果輸入是以 / 開頭的斜線指令流程
		if (value.startsWith('/')) {
			// 無論指令對錯，先將使用者的敲擊輸入印在歷史瀑布流中
			setMessages(prev => [...prev, { role: 'user', text: value }]);
			setInputKey(prev => prev + 1);

			// 支援快捷退出選單 (/bye 或原本的 /exit)
			if (trimmed === '/bye' || trimmed === '/exit') {
				onBack();
				return;
			}

			// 處理 /? 或 /help 幫助面板
			if (trimmed === '/?' || trimmed === '/help') {
				const helpMenu = [
					"Available Commands:",
					"  /set            Set session variables",
					"  /show           Show model information",
					"  /load <model>   Load a session or model",
					"  /save <model>   Save your current session",
					"  /clear          Clear session context",
					"  /bye            Exit",
					"  /?, /help       Help for a command",
					"  /? shortcuts    Help for keyboard shortcuts",
					"",
					"Use \"\"\" to begin a multi-line message."
				].join('\n');

				setMessages(prev => [...prev, { role: 'system_reply', text: helpMenu }]);
				return;
			}

			// 處理其他未知的斜線指令 (例如單獨輸入 "/")
			setMessages(prev => [...prev, {
				role: 'system_reply',
				text: `Unknown command '${value}'. Type /? for help`
			}]);
			return;
		}

		// 🎯 規則 3：常規對話文字，送往 Java API / MockAPI 進行推理
		setMessages(prev => [...prev, { role: 'user', text: value }]);
		setIsLoading(true);
		setInputKey(prev => prev + 1);

		try {
			const response: ChatResponse = await chat(value);
			setMessages(prev => [...prev, { role: 'assistant', text: response.answer }]);
		} catch {
			setMessages(prev => [...prev, { role: 'assistant', text: "❌ 系統異常：與遠端 Java 服務中斷連線。" }]);
		} finally {
			setIsLoading(false);
		}
	};

	// ─── 畫面渲染分流 ───
	if (isOneShot) {
		if (isLoading || !oneShotAnswer) {
			return (
				<Box paddingX={0} paddingTop={0}>
					<Text color="yellow" bold>⏳ 正在連線至 Java API 進行 LLM 深度推理... 請稍候...</Text>
				</Box>
			);
		}
		return (
			<Box flexDirection="column" paddingX={0} paddingTop={0}>
				<Text bold color="white">&gt;&gt;&gt; {oneShotArgs}</Text>
				<Box marginTop={0}>
					<Text color="white">{oneShotAnswer}</Text>
				</Box>
			</Box>
		);
	}

	return (
		<Box flexDirection="column" paddingX={0} paddingTop={0}>
			{/* 歷史對話瀑布流 (拿掉所有外層圍牆，改為 Ollama 的極簡緊湊行距排版) */}
			<Box flexDirection="column">
				{messages.map((msg, i) => {
					if (msg.role === 'user') {
						// 如果 text 是空的，剛好只會渲染出 >>> ，完美重現空 Enter 換行感
						return (
							<Box key={i}>
								<Text bold color="white">&gt;&gt;&gt; {msg.text}</Text>
							</Box>
						);
					} else {
						// assistant 與 system_reply 直接靠左直落輸出
						return (
							<Box key={i}>
								<Text color="white">{msg.text}</Text>
							</Box>
						);
					}
				})}
			</Box>

			{/* 底層動態輸入核心區 */}
			{isLoading ? (
				<Box>
					<Text color="gray">⠋ Thinking...</Text>
				</Box>
			) : (
				<Box flexDirection="row">
					<Text bold color="white">&gt;&gt;&gt; </Text>
					{/* 💡 注入指定的 Ollama 專屬灰色提示字 placeholder */}
					<TextInput
						key={inputKey}
						placeholder="Send a message (/? for help)"
						onSubmit={handleInteractiveSubmit}
					/>
				</Box>
			)}
		</Box>
	);
};
