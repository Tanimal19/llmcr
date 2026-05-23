#!/usr/bin/env node
import { render } from 'ink';
import meow from 'meow';
import App from './app.js';

const cli = meow(`
	使用說明：
	  $ tui                     - 進入互動式主選單

	單次/捷徑指令 (Shortcut Mode)：
	  $ tui --chat "<問題>"      - 單次詢問 LLM 並退出
	  $ tui --review            - 直接跑完進度條並退出
	  $ tui --setrag            - 跳過主選單，直接進入 RAG 檔案選擇畫面
`, {
	importMeta: import.meta,
	flags: {
		chat: { type: 'string', shortFlag: 'c' },
		review: { type: 'boolean', shortFlag: 'r' },
		setrag: { type: 'boolean', shortFlag: 's' } // 💡 改為 boolean
	}
});

const oneShotFlags = {
	chat: cli.flags.chat,
	review: cli.flags.review,
	setrag: cli.flags.setrag
};

render(<App oneShotFlags={oneShotFlags} />);
