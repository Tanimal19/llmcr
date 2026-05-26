import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import test from 'ava';
import { render } from 'ink-testing-library';
import { ReviewCommand } from './review.cmd.js';
import { delay, setupMockFetch, createSseResponse } from '#testing-helpers.js';

test.afterEach(() => {
  // @ts-expect-error fetch is non-configurable in lib dom types, but we clear it in tests.
  delete globalThis.fetch;
});

test.serial('ReviewCommand renders review summary from SSE result', async t => {
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
            reportPath: randomReportPath,
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
      diffPath={randomDiffPath}
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
