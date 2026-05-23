#!/usr/bin/env node
import {render} from 'ink';
import meow from 'meow';
import App from './app.js';

const cli = meow(
	`
	Usage
      $ tui                     - 進入互動式主畫面
	  $ tui --chat "<question>"      - 單次詢問 LLM 並直接返回終端機

	Options
		--chat, -c   單次執行對話，回答完後立刻結束程式

	Examples
	  $ tui --chat "What is the repo name?"
`,
	{
		importMeta: import.meta,
		flags: {
			chat: {
				type: 'string',
			    shortFlag: 'c', // 支援短指令 -c
			},
		},
	},
);

render(<App oneShotChat={cli.flags.chat} />);
