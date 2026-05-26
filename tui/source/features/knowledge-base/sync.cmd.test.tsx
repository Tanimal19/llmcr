import test from 'ava';
import { render } from 'ink-testing-library';
import { SyncCommand } from './sync.cmd.js';
import { delay, setupMockFetch, jsonResponse, createSseResponse } from '#testing-helpers.js';

test.afterEach(() => {
  // @ts-expect-error fetch is non-configurable in lib dom types, but we clear it in tests.
  delete globalThis.fetch;
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
