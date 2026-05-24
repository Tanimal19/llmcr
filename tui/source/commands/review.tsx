import { useState, useEffect } from 'react';
import { Box, Text, useInput } from 'ink';
import { CommandProps } from '../types.js'; // 引入我們定義的共同介面
import { reviewWithProgress, ReviewErrorEvent, ReviewStageProgress } from '../api.js';

interface ReviewCommandProps extends CommandProps {
  diffPath?: string;
	useMock?: boolean;
}

export const ReviewCommand = ({ onBack, diffPath, useMock = false }: ReviewCommandProps) => {
	const [progress, setProgress] = useState(0);
	const [stageMessage, setStageMessage] = useState('Waiting to start review...');
	const [status, setStatus] = useState<'running' | 'success' | 'error'>('running');
	const [errorMessage, setErrorMessage] = useState<string | null>(null);
	const [progressLogs, setProgressLogs] = useState<string[]>([]);

	const appendLog = (message: string): void => {
		setProgressLogs(previous => [...previous, message]);
	};

	// Allow user to leave the view with ESC.
	useInput((_, key) => {
		if (key.escape) {
			onBack();
		}
	});

	useEffect(() => {
		if ((!diffPath || diffPath.trim().length === 0) && !useMock) {
			setStatus('error');
			setErrorMessage('Diff path is required.');
			setStageMessage('Review did not start');
			appendLog('[ERROR] Review did not start: Diff path is required.');
			return;
		}

		if (useMock) {
			setStageMessage('Using mock review data...');
			appendLog('[INFO] Using mock review data');
		}

		appendLog('[INFO] Review started');

		const abortController = new AbortController();
		const updateProgress = (event: ReviewStageProgress): void => {
			const percent = event.total > 0
				? Math.max(0, Math.min(100, Math.round((event.current / event.total) * 100)))
				: 0;
			setProgress(percent);
			setStageMessage(`${event.stage} (${event.status}) - ${event.message}`);
			appendLog(`[${event.stage}] ${event.status} - ${event.message} (${percent}%)`);
		};

		const updateError = (event: ReviewErrorEvent): void => {
			setStatus('error');
			setErrorMessage(`${event.code}: ${event.message}`);
			setStageMessage('Review failed');
			appendLog(`[ERROR] ${event.code}: ${event.message}`);
		};

		reviewWithProgress(diffPath ?? '', {
			onProgress: updateProgress,
			onError: updateError,
			useMock,
			onResult: () => {
				setProgress(100);
				setStatus('success');
				setStageMessage('Review completed successfully');
				appendLog('[DONE] Review completed successfully');
			},
			signal: abortController.signal,
		}).catch(error => {
			setStatus('error');
			setErrorMessage(error instanceof Error ? error.message : String(error));
			setStageMessage('Review failed');
			appendLog(`[ERROR] ${error instanceof Error ? error.message : String(error)}`);
		});

		return () => {
			abortController.abort();
			appendLog('[INFO] Review stream aborted');
		};
	}, [diffPath, useMock]);

  const barWidth = 20;
  const completedWidth = Math.round((progress / 100) * barWidth);
  const progressBar = '█'.repeat(completedWidth) + '░'.repeat(barWidth - completedWidth);

	return (
		<Box flexDirection="column" padding={1}>
			<Text color="magenta" bold>
				📊 Preforming code review...
			</Text>
			{diffPath && (
				<Text color="gray">Diff path: {diffPath}</Text>
			)}
			{useMock && <Text color="yellow">Mode: Mock Data</Text>}
			<Text color="gray">{stageMessage}</Text>
			<Box marginTop={1}>
				<Text color={status === 'error' ? 'red' : 'green'}>[{progressBar}] </Text>
				<Text>{progress}%</Text>
			</Box>
			<Box flexDirection="column" marginTop={1}>
				<Text color="cyan">Progress Log:</Text>
				{progressLogs.length === 0 && <Text color="gray">(no events yet)</Text>}
				{progressLogs.map((log, index) => (
					<Text key={`${index}-${log}`} color="gray">{`${index + 1}. ${log}`}</Text>
				))}
			</Box>
			{status === 'success' && <Text color="green">Review done. Press ESC to return.</Text>}
			{status === 'error' && <Text color="red">Error: {errorMessage ?? 'Unknown error'}</Text>}
		</Box>
	);
};
