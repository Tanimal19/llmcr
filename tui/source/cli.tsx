#!/usr/bin/env node
import {render} from 'ink';
import meow from 'meow';
import App from './app.js';

const cli = meow(
  `
  使用說明:
  $ llmcr                     - 進入互動式主選單

  單次/捷徑指令 (Shortcut Mode):
  $ llmcr --chat, -c "<question>" - 單次詢問 LLM 並退出
  $ llmcr --review, -r            - 直接跑完進度條並退出
  $ llmcr --setrag, -s            - 直接進入 RAG 檔案勾選畫面並退出
  $ llmcr --lsdb, -l              - 查看目前知識庫檔案清單並退出
`,
  {
    importMeta: import.meta,
    flags: {
      chat: {type: 'string', shortFlag: 'c'},
      review: {type: 'boolean', shortFlag: 'r'},
      setrag: {type: 'boolean', shortFlag: 's'},
      lsdb: {type: 'boolean', shortFlag: 'l'},
    },
  },
);

const oneShotFlags = {
  chat: cli.flags.chat,
  review: cli.flags.review,
  setrag: cli.flags.setrag,
  lsdb: cli.flags.lsdb,
};

render(<App oneShotFlags={oneShotFlags} />);
