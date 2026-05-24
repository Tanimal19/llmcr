import React from 'react';
import test from 'ava';
import { render } from 'ink-testing-library';
import { ChatCommand } from './source/commands/chat.js';
import { LsDbCommand } from './source/commands/lsdb.js';
import { SetRagCommand } from './source/commands/setrag.js';

// ─── 💡 輔助工具 1：非同步等待刷新 ───
// 修正點：加上大括號 {} 避免 setTimeout 回傳的 ID 被 Promise 執行器錯誤地隱式 return
const delay = async (ms: number) =>
  new Promise<void>(resolve => {
    setTimeout(resolve, ms);
  });

// ─── 💡 輔助工具 2：全域 Fetch 模擬器 ───
// 修正點：將 any 改為 unknown。直接使用原生的 new Response()，徹底移除不安全的類型斷言 (as Response)
const setupMockFetch = (mockResponseData: unknown, status = 200) => {
  globalThis.fetch = async () =>
    Response.json(mockResponseData, {
      status,
      headers: { 'content-type': 'application/json' },
    });
};

// 在每個測試結束後清理 Fetch 模擬，避免污染環境
test.afterEach(() => {
  // 修正點：遵照 XO 規範，將不推薦的 @ts-ignore 改為現代的 @ts-expect-error
  // @ts-expect-error - fetch 在 globalThis 上預設不可刪除，此處僅用於測試清理
  delete globalThis.fetch;
});

// ─── 🎯 測試個案 1：Chat 指令測試 ───
test.serial('ChatCommand - 應能正常輸入訊息並渲染 AI 的回應', async t => {
  setupMockFetch({
    answer: '這是來自 Java API 模擬的智慧回應。',
    retrievedContexts: {},
  });

  // 修正點：為空箭頭函式補上註解 /* No-op */ 並縮回單行，同時滿足 Prettier 與 XO 的空函式限制
  const { lastFrame, stdin, unmount } = render(
    <ChatCommand
      onBack={() => {
        /* No-op */
      }}
    />,
  );

  // 1. 模擬使用者打字（改用英文更穩健）
  stdin.write('hello');
  await delay(50); // 💡 給 TextInput 一點時間把字吃進去

  // 2. 按下 Enter 鍵
  stdin.write('\r');
  await delay(150); // 等待 API 異步回傳與畫面刷新

  // 💡 【除錯法寶】如果還是失敗，這行會把畫面完整印在終端機上供你檢查
  console.log('=== Chat 畫面實況 ===\n', lastFrame(), '\n====================');

  // 修正點：依據安全規範，將帶有布林偽值風險的 || 替換為空值合併運算子 ??
  const frameText = lastFrame() ?? '';
  t.true(frameText.includes('hello'));
  t.true(frameText.includes('這是來自 Java API 模擬的智慧回應。'));

  unmount();
});

// ─── 🎯 測試個案 2：LsDB 查看知識庫測試 ───
test.serial('LsDbCommand - 應能載入並渲染正確的知識庫列表與 Source 數量', async t => {
  setupMockFetch([
    {
      id: 1,
      path: '/Users/project/demo-repo',
      isSynced: true,
      lastSyncTime: '2026-05-24 12:00:00',
      sources: [
        { id: 101, path: 'src/main.ts', type: 'TS', syncStatus: 'SYNCED' },
        { id: 102, path: 'package.json', type: 'JSON', syncStatus: 'SYNCED' },
      ],
    },
  ]);

  const { lastFrame, unmount } = render(
    <LsDbCommand
      onBack={() => {
        /* No-op */
      }}
    />,
  );

  await delay(100);

  // 💡 【除錯法寶】印出 LsDb 的實際畫面
  console.log('=== LsDb 畫面實況 ===\n', lastFrame(), '\n====================');

  const frameText = lastFrame() ?? '';

  t.true(frameText.includes('demo-repo'));
  t.true(frameText.includes('Synced · 2 sources'));
  t.true(frameText.includes('main.ts')); // 💡 修正點：因為 toLabel() 會把路徑切到只剩檔名！

  unmount();
});

// ─── 🎯 測試個案 3：SetRAG 配置文件清單測試 ───
test.serial('SetRagCommand - 應能載入 Scope 狀態並渲染核取方塊', async t => {
  // 1. 準備符合 Record<string, boolean> 格式的 Mock 資料
  setupMockFetch({
    '/Users/project/repo-A': true,
    '/Users/project/repo-B': false,
  });

  const { lastFrame, unmount } = render(
    <SetRagCommand
      onBack={() => {
        /* No-op */
      }}
    />,
  );

  // 等待異步資料載入與排序處理
  await delay(100);

  // 💡 【除錯法寶】印出 SetRag 的實際畫面
  console.log('=== SetRag 畫面實況 ===\n', lastFrame(), '\n====================');

  const frameText = lastFrame() ?? '';

  // 2. 驗證標題與選擇數量統計
  t.true(frameText.includes('RAG Scope'));
  t.true(frameText.includes('Selected: 1/2')); // 因為一個 true 一个 false

  // 3. 驗證專案標籤解析與 Checkbox 符號是否存在 (● 代表選中, ○ 代表未選)
  t.true(frameText.includes('repo-A'));
  t.true(frameText.includes('repo-B'));

  unmount();
});
