import test from 'ava';
import { render } from 'ink-testing-library';
import { SetRagCommand } from './setrag.cmd.js';
import { delay, setupMockFetch, jsonResponse } from '#testing-helpers.js';

test.afterEach(() => {
  // @ts-expect-error fetch is non-configurable in lib dom types, but we clear it in tests.
  delete globalThis.fetch;
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
