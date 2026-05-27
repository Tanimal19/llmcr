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

test.serial('ReviewCommand outputs review progress logs from SSE events', async t => {
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
              content: {
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
              checklistItems: [],
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
  t.true(frameText.includes('Progress Log:'));
  t.true(frameText.includes('[INFO] Review started'));
  t.true(frameText.includes('[INFO] Review task started: review (review-1)'));
  t.true(frameText.includes('[ANALYZE] INFO - Reading diff'));
  t.true(frameText.includes('[DONE] Review completed successfully'));

  unmount();
});

test.serial('ReviewCommand renders review result preview from SSE result', async t => {
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
              content: {
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
              checklistItems: [],
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
  t.true(frameText.includes('Checklist Items: 0'));
  t.true(frameText.includes('Issues: 2'));
  t.true(frameText.includes('Overview'));
  t.true(frameText.includes('Motivation'));
  t.true(frameText.includes('Improve reliability'));
  t.true(frameText.includes('Suggestion'));
  t.true(frameText.includes('Add validation test'));
  t.true(frameText.includes('Good Points'));
  t.true(frameText.includes('Clear separation of concerns'));
  t.true(frameText.includes('Bad Points'));
  t.true(frameText.includes('Missing edge-case test'));
  t.true(frameText.includes('Implementation Details'));
  t.true(frameText.includes('File: src/parser.ts'));
  t.true(frameText.includes('Adds retry logic'));
  t.true(frameText.includes('1. [BUG] Nullable value not checked @ src/parser.ts:42'));
  t.true(frameText.includes('Detail: Potential runtime error'));

  unmount();
});

test.serial('ReviewCommand handles missing summary payload without crashing', async t => {
  const secureTmpDir = os.tmpdir();
  const randomDiffPath = path.join(secureTmpDir, `pr-${crypto.randomUUID()}.json`);
  const randomReportPath = path.join(secureTmpDir, `review-${crypto.randomUUID()}.md`);

  setupMockFetch(url => {
    if (url.endsWith('/review')) {
      return createSseResponse([
        { event: 'start', data: { name: 'review', id: 'review-2' } },
        { event: 'progress', data: { isError: false, stage: 'ANALYZE', message: 'Reading diff' } },
        {
          event: 'result',
          data: {
            reportPath: randomReportPath,
            reviewReport: {
              prId: 456,
              prTitle: 'Handle sparse report payload',
              interpretation: {
                changeDescription: 'No summary generated',
                changeMotivation: 'Pipeline returned partial data',
              },
              checklistItems: [],
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
  t.true(frameText.includes('Review done. Press ESC to return.'));
  t.true(frameText.includes('PR: #456 Handle sparse report payload'));
  t.true(frameText.includes('Overview'));
  t.true(frameText.includes('Motivation'));
  t.true(frameText.includes('Suggestion'));
  t.true(frameText.includes('Good Points'));
  t.true(frameText.includes('Bad Points'));
  t.true(frameText.includes('Implementation Details'));
  t.true(frameText.includes('Checklist'));
  t.true(frameText.includes('Checklist Items: 0'));
  t.true(frameText.includes('Issues: 0'));
  t.true(frameText.includes('(none)'));

  unmount();
});
