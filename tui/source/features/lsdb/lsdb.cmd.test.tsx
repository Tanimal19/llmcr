import test from 'ava';
import { render } from 'ink-testing-library';
import { LsDbCommand } from './lsdb.cmd.js';
import { delay, setupMockFetch, jsonResponse } from '#testing-helpers.js';

test.afterEach(() => {
  // @ts-expect-error fetch is non-configurable in lib dom types, but we clear it in tests.
  delete globalThis.fetch;
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
