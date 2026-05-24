import React from 'react';
import test from 'ava';
import { render } from 'ink-testing-library';
import { ChatCommand } from './source/commands/chat.js';
import { LsDbCommand } from './source/commands/lsdb.js';
import { SetRagCommand } from './source/commands/setrag.js';

// ─── 💡 輔助工具 1：非同步等待刷新 ───
// 因為 API 請求是異步的，我們需要一個小工具讓測試暫停、等待 React 完成 State 更新與重新渲染
const delay = async (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

// ─── 💡 輔助工具 2：全域 Fetch 模擬器 ───
const setupMockFetch = (mockResponseData: any, status = 200) => {
  globalThis.fetch = async () => {
    return {
      ok: status === 200,
      status,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => mockResponseData,
      text: async () => JSON.stringify(mockResponseData),
    } as Response;
  };
};

// 在每個測試結束後清理 Fetch 模擬，避免污染環境
test.afterEach(() => {
  // @ts-ignore
  delete globalThis.fetch;
});

// ─── 🎯 測試個案 1：Chat 指令測試 ───
test.serial('ChatCommand - 應能正常輸入訊息並渲染 AI 的回應', async t => {
  setupMockFetch({
    answer: '這是來自 Java API 模擬的智慧回應。',
    retrievedContexts: {},
  });

  const { lastFrame, stdin, unmount } = render(<ChatCommand onBack={() => {
}} />);

  // 1. 模擬使用者打字（改用英文更穩健）
  stdin.write('hello');
  await delay(50); // 💡 給 TextInput 一點時間把字吃進去

  // 2. 按下 Enter 鍵
  stdin.write('\r');
  await delay(150); // 等待 API 異步回傳與畫面刷新

  // 💡 【除錯法寶】如果還是失敗，這行會把畫面完整印在終端機上供你檢查
  console.log('=== Chat 畫面實況 ===\n', lastFrame(), '\n====================');

  const frameText = lastFrame() || '';
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

  const { lastFrame, unmount } = render(<LsDbCommand onBack={() => {
}} />);

  await delay(100);

  // 💡 【除錯法寶】印出 LsDb 的實際畫面
  console.log('=== LsDb 畫面實況 ===\n', lastFrame(), '\n====================');

  const frameText = lastFrame() || '';

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

  const { lastFrame, unmount } = render(<SetRagCommand onBack={() => {
}} />);

  // 等待異步資料載入與排序處理
  await delay(100);

  const frameText = lastFrame() || '';

  // 2. 驗證標題與選擇數量統計
  t.true(frameText.includes('RAG Scope'));
  t.true(frameText.includes('Selected: 1/2')); // 因為一個 true 一个 false

  // 3. 驗證專案標籤解析與 Checkbox 符號是否存在 (● 代表選中, ○ 代表未選)
  t.true(frameText.includes('repo-A'));
  t.true(frameText.includes('repo-B'));

  unmount();
});
