import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import test from 'ava';
import { render } from 'ink-testing-library';
import { PreviewCommand } from './preview.cmd.js';
import { delay } from '#testing-helpers.js';

test.serial('PreviewCommand renders report summary from JSON file', async t => {
  const tempDirectory = await mkdtemp(path.join(os.tmpdir(), 'llmcr-preview-'));
  const reportPath = path.join(tempDirectory, `${crypto.randomUUID()}.json`);

  await writeFile(
    reportPath,
    JSON.stringify({
      prId: 901,
      prTitle: 'Add report preview mode',
      content: {
        motivation: 'Help users inspect reports quickly',
        goodPoints: ['Simple command flow'],
        badPoints: ['Needs CLI arg support later'],
        suggestion: 'Add search/filter later',
        implementationDetails: [{ filename: 'source/features/preview/preview.cmd.tsx', details: ['Loads JSON file'] }],
        issues: [
          {
            type: 'STYLE',
            title: 'Tight coupling in parser',
            location: 'source/features/preview/preview.cmd.tsx:18',
            detail: 'Extract parser helper',
          },
        ],
      },
      interpretation: {
        changeDescription: 'Adds preview command',
        changeMotivation: 'Improve usability',
      },
      checklistItems: [],
    }),
    'utf8',
  );

  try {
    const { lastFrame, unmount } = render(
      <PreviewCommand
        reportPath={reportPath}
        onBack={() => {
          /* No-op */
        }}
      />,
    );

    await delay(120);

    const frameText = lastFrame() ?? '';
    t.true(frameText.includes('Previewing review report:'));
    t.true(frameText.includes(path.basename(reportPath)));
    t.true(frameText.includes('Report loaded'));
    t.true(frameText.includes('PR: #901 Add report preview mode'));
    t.true(frameText.includes('Good Points'));
    t.true(frameText.includes('- Simple command flow'));
    t.true(frameText.includes('Issues: 1'));
    t.true(frameText.includes('1. [STYLE] Tight coupling in parser @ source/features/preview/preview.cmd.tsx:18'));

    unmount();
  } finally {
    await rm(tempDirectory, { recursive: true, force: true });
  }
});

test.serial('PreviewCommand renders error for invalid report payload', async t => {
  const tempDirectory = await mkdtemp(path.join(os.tmpdir(), 'llmcr-preview-'));
  const reportPath = path.join(tempDirectory, `${crypto.randomUUID()}.json`);

  await writeFile(reportPath, JSON.stringify({ foo: 'bar' }), 'utf8');

  try {
    const { lastFrame, unmount } = render(
      <PreviewCommand
        reportPath={reportPath}
        onBack={() => {
          /* No-op */
        }}
      />,
    );

    await delay(120);

    const frameText = lastFrame() ?? '';
    t.true(frameText.includes('Failed to load report'));
    t.true(frameText.includes('Invalid report JSON'));

    unmount();
  } finally {
    await rm(tempDirectory, { recursive: true, force: true });
  }
});
