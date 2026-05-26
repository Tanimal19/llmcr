import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import test from 'ava';
import { render } from 'ink-testing-library';
import { ChatCommand } from '#screens/interactive/chat.js';
import { ReviewCommand } from '#screens/stream/review.js';
import { SyncCommand } from '#screens/stream/sync.js';
import { LsDbCommand } from '#screens/database/lsdb.js';
import { SetRagCommand } from '#screens/database/setrag.js';

const delay = async (ms: number) =>
  new Promise<void>(resolve => {
    setTimeout(resolve, ms);
  });

const setupMockFetch = (handler: (url: string, init?: RequestInit) => Response | Promise<Response>) => {
  globalThis.fetch = async (input: URL | RequestInfo, init?: RequestInit) => {
    let url: string;

    if (typeof input === 'string') {
      url = input;
    } else if (input instanceof URL) {
      url = input.toString();
    } else {
      url = input.url;
    }

    return handler(url, init);
  };
};

const jsonResponse = (value: unknown, status = 200) =>
  Response.json(value, {
    status,
    headers: { 'content-type': 'application/json' },
  });

const createSseResponse = (events: Array<{ event: string; data: unknown }>) => {
  const payload = events
    .map(({ event, data }) => {
      const encodedData = typeof data === 'string' ? data : JSON.stringify(data);
      return `event: ${event}\ndata: ${encodedData}`;
    })
    .join('\n\n');

  return new Response(payload, {
    status: 200,
    headers: { 'content-type': 'text/event-stream' },
  });
};

test.afterEach(() => {
  // @ts-expect-error fetch is non-configurable in lib dom types, but we clear it in tests.
  delete globalThis.fetch;
});

test.serial('ChatCommand renders user input and assistant reply', async t => {
  setupMockFetch(url => {
    if (url.endsWith('/chat')) {
      return jsonResponse({
        answer: 'This is a mocked assistant response.',
        retrievedContexts: {},
      });
    }

    throw new Error(`Unexpected URL: ${url}`);
  });

  const { lastFrame, stdin, unmount } = render(
    <ChatCommand
      onBack={() => {
        /* No-op */
      }}
    />,
  );

  stdin.write('hello');
  await delay(50);
  stdin.write('\r');
  await delay(150);

  const frameText = lastFrame() ?? '';
  t.true(frameText.includes('hello'));
  t.true(frameText.includes('This is a mocked assistant response.'));

  unmount();
});

test.serial('LsDbCommand renders track roots and source counts', async t => {
  setupMockFetch(url => {
    if (url.endsWith('/lsdb')) {
      return jsonResponse([
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
    }

    throw new Error(`Unexpected URL: ${url}`);
  });

  const { lastFrame, unmount } = render(
    <LsDbCommand
      onBack={() => {
        /* No-op */
      }}
    />,
  );

  await delay(100);

  const frameText = lastFrame() ?? '';

  t.true(frameText.includes('demo-repo'));
  t.true(frameText.includes('Synced · 2 sources'));
  t.true(frameText.includes('main.ts'));

  unmount();
});

test.serial('SetRagCommand renders rag scope and selection count', async t => {
  setupMockFetch(url => {
    if (url.endsWith('/getrag')) {
      return jsonResponse({
        '/Users/project/repo-A': true,
        '/Users/project/repo-B': false,
      });
    }

    throw new Error(`Unexpected URL: ${url}`);
  });

  const { lastFrame, unmount } = render(
    <SetRagCommand
      onBack={() => {
        /* No-op */
      }}
    />,
  );

  await delay(100);

  const frameText = lastFrame() ?? '';

  t.true(frameText.includes('RAG Scope'));
  t.true(frameText.includes('Selected: 1/2'));
  t.true(frameText.includes('repo-A'));
  t.true(frameText.includes('repo-B'));

  unmount();
});

test.serial('SyncCommand completes sync flow and prints summary', async t => {
  setupMockFetch(url => {
    if (url.endsWith('/sync')) {
      return createSseResponse([
        { event: 'start', data: { name: 'sync', id: 'sync-1' } },
        { event: 'progress', data: { isError: false, stage: 'INDEX', message: 'Running indexers' } },
        { event: 'result', data: {} },
      ]);
    }

    if (url.endsWith('/lsdb')) {
      return jsonResponse([
        {
          id: 1,
          path: '/Users/project/demo-repo',
          isSynced: false,
          lastSyncTime: undefined,
          sources: [],
        },
      ]);
    }

    throw new Error(`Unexpected URL: ${url}`);
  });

  const { lastFrame, unmount } = render(
    <SyncCommand
      onBack={() => {
        /* No-op */
      }}
    />,
  );

  await delay(180);

  const frameText = lastFrame() ?? '';
  t.true(frameText.includes('Performing source sync using backend SSE stream'));
  t.true(frameText.includes('Sync done. Press ESC to return.'));
  t.true(frameText.includes('Track roots before sync: 1 total, 1 unsynced'));
  t.true(frameText.includes('Track roots after sync: 1 total, 1 unsynced'));

  unmount();
});

test.serial('ReviewCommand renders review summary from SSE result', async t => {
  // 👇 動態生成隨機且安全的暫存路徑
  const secureTmpDir = os.tmpdir();
  const randomDiffPath = path.join(secureTmpDir, `pr-${crypto.randomUUID()}.json`);
  const randomReportPath = path.join(secureTmpDir, `review-${crypto.randomUUID()}.md`);

  setupMockFetch(url => {
    if (url.endsWith('/review')) {
      return createSseResponse([
        { event: 'start', data: { name: 'review', id: 'review-1' } },
        { event: 'progress', data: { isError: false, stage: 'ANALYZE', message: 'Reading diff' } },
        {
          event: 'result',
          data: {
            reportPath: randomReportPath, // 👈 1. 這裡改用隨機路徑变量
            reviewReport: {
              prId: 123,
              prTitle: 'Improve parser stability',
              mainReport: {
                motivation: 'Improve reliability',
                goodPoints: ['Clear separation of concerns'],
                badPoints: ['Missing edge-case test'],
                suggestion: 'Add validation test',
                implementationDetails: [{ filename: 'src/parser.ts', details: ['Adds retry logic'] }],
                issues: [
                  {
                    type: 'BUG',
                    title: 'Nullable value not checked',
                    location: 'src/parser.ts:42',
                    detail: 'Potential runtime error',
                  },
                  {
                    type: 'STYLE',
                    title: 'Inconsistent naming',
                    location: 'src/parser.ts:77',
                    detail: 'Use camelCase',
                  },
                ],
              },
              interpretation: {
                changeDescription: 'Parser handling updates',
                changeMotivation: 'Reduce failures',
              },
              itemAnswers: [],
            },
          },
        },
      ]);
    }

    throw new Error(`Unexpected URL: ${url}`);
  });

  const { lastFrame, unmount } = render(
    <ReviewCommand
      diffPath={randomDiffPath} // 👈 2. 這裡也改用隨機路徑变量
      onBack={() => {
        /* No-op */
      }}
    />,
  );

  await delay(180);

  const frameText = lastFrame() ?? '';
  t.true(frameText.includes('Performing code review on:'));
  t.true(frameText.includes(path.basename(randomDiffPath)));
  t.true(frameText.includes('Review done. Press ESC to return.'));
  t.true(frameText.includes('PR: #123 Improve parser stability'));
  t.true(frameText.includes('Issues: 2'));
  t.true(frameText.includes('1. [BUG] Nullable value not checked @ src/parser.ts:42'));

  unmount();
});
