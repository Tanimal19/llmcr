import { useState } from 'react';
import { Box, Text, useInput } from 'ink';
import { TextInput } from '@inkjs/ui';
import { CommandProps } from '../types.js';

// ────────────────────────────────────────────────────────
// 💡 精準對齊 Java Record 結構的 DTO 型態定義
// ────────────────────────────────────────────────────────
interface ChatRequest {
	query: string;
}

interface ChatResponse {
	answer: string;
	retrievedContexts: Record<string, number>; // 對應 Java 的 Map<String, Float>
}

interface Message {
	role: 'user' | 'assistant';
	text: string;
}

// ────────────────────────────────────────────────────────
// 🧠 Mock API: 完美模擬 Java 後端 @PostMapping("/chat") 行為
// ────────────────────────────────────────────────────────
const mockChatCall = async (request: ChatRequest): Promise<ChatResponse> => {
	return new Promise((resolve) => {
		setTimeout(() => {
			const query = request.query.trim().toLowerCase();
			let answer = "Hello! How can I assist you today? Feel free to ask me any questions or let me know if you need help with anything specific.";

			// 依據關鍵字進行簡單的 Mock 語意分流
			if (query === 'hi' || query === 'hello') {
				answer = "Hello! How can I assist you today? Feel free to ask me any questions or let me know if you need help with anything specific.";
			} else if (query.includes('review') || query.includes('code')) {
				answer = "I can definitely help you review your code! Please provide the specific git diff or file path you'd like me to look into.";
			} else if (query.includes('status')) {
				answer = "All internal pipelines are nominal. Simulated Java APIService is listening on port 8080.";
			} else {
				answer = `I received your query: "${request.query}". This is a structured mock response streamed from your simulated Java backend context.`;
			}

			resolve({
				answer,
				// 模擬動態 RAG 檢索命中的上下文權重，保留擴充彈性
				retrievedContexts: {
					"src/main/java/APIService.java": 0.945,
					"src/main/resources/prompt.txt": 0.812
				}
			});
		}, 750); // 模擬 750 毫秒的網絡往返與 LLM 執行延遲
	});
};

// ────────────────────────────────────────────────────────
// 核心組件：Ollama 風格對話核心
// ────────────────────────────────────────────────────────
export const ChatCommand = ({ onBack }: CommandProps) => {

	// --- 狀態定義 ---
	const [messages, setMessages] = useState<Message[]>([]);
	const [inputKey, setInputKey] = useState(0);
	const [isLoading, setIsLoading] = useState(false);

	// --- 鍵盤事件監聽：隨時按 [Esc] 滑順退回主選單 ---
	useInput((_, key) => {
		if (key.escape) {
			onBack();
		}
	});

	// --- 互動對話模式 ---
	const handleInteractiveSubmit = async (value: string) => {
		const trimmed = value.trim();

		// 支援輸入 /exit 退出，與 Esc 鍵雙軌並行
		if (trimmed === '/exit') {
			onBack();
			return;
		}
		if (trimmed === '' || isLoading) return;

		// 1. 立即將使用者的問題推入對話瀑布流
		setMessages(prev => [...prev, { role: 'user', text: trimmed }]);
		setIsLoading(true);
		setInputKey(prev => prev + 1); // 立即重構並清空輸入框，重現 Ollama 順暢無滯後的敲擊感

		try {
			// 2. 封裝標準 Request 並發送給 Mock 服務
			const response = await mockChatCall({ query: trimmed });

			// 3. 將 Java DTO 解析出的答案追加至畫面
			setMessages(prev => [...prev, { role: 'assistant', text: response.answer }]);
		} catch (error) {
			setMessages(prev => [...prev, { role: 'assistant', text: "❌ 系統異常：與遠端 Java 服務中斷連線。" }]);
		} finally {
			setIsLoading(false);
		}
	};

	return (
		<Box flexDirection="column" paddingX={2} paddingTop={1}>
			{/* 歷史對話紀錄：拿掉所有冗餘的 UI 裝飾，純粹呈現文字流 */}
			<Box flexDirection="column">
				{messages.map((msg, i) => (
					<Box key={i} flexDirection="column" marginBottom={1}>
						{msg.role === 'user' ? (
							<Text bold color="white">&gt;&gt;&gt; {msg.text}</Text>
						) : (
							<Text color="white">{msg.text}</Text>
						)}
					</Box>
				))}
			</Box>

			{/* 動態輸入控制區 */}
			{isLoading ? (
				<Box marginBottom={1}>
					<Text color="gray">⠋ Thinking...</Text>
				</Box>
			) : (
				<Box flexDirection="row">
					<Text bold color="white">&gt;&gt;&gt; </Text>
					<TextInput
						key={inputKey}
						placeholder=""
						onSubmit={handleInteractiveSubmit}
					/>
				</Box>
			)}
		</Box>
	);
};
