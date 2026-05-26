import test from 'ava';
import { render } from 'ink-testing-library';
import { ChatCommand } from './chat.cmd.js';
import { delay, setupMockFetch, jsonResponse } from '#testing-helpers.js';

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
