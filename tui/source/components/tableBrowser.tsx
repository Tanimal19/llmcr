import { useEffect, useMemo, useState } from 'react';
import { Box, Text, useInput } from 'ink';

const DEFAULT_PAGE_SIZE = 10;

export interface TableBrowserItem {
	id: string;
	label: string;
	checked?: boolean;
	rightText?: string;
	rightColor?: 'white' | 'gray' | 'green' | 'yellow' | 'red' | 'cyan';
}

interface TableBrowserProps {
	title: string;
	subtitle?: string;
	items: TableBrowserItem[];
	themeColor?: 'white' | 'gray' | 'green' | 'yellow' | 'red' | 'cyan';
	pageSize?: number;
	showCheckbox?: boolean;
	showLineNumbers?: boolean;
	cursorSymbol?: string;
	checkedSymbol?: string;
	uncheckedSymbol?: string;
	loading: boolean;
	loadingText: string;
	errorText?: string;
	errorTitle?: string;
	errorEnterAction?: 'escape' | 'clear';
	statusText?: string;
	escapeHint: string;
	leftHelpLines?: string[];
	rightHelpLines?: string[];
	onEscape: () => void;
	onEnter: () => void;
	onToggleCurrent?: (index: number) => void;
	onToggleAll?: () => void;
	onSwitchTable?: () => void;
	onClearError?: () => void;
}

export const TableBrowser = ({
	title,
	subtitle,
	items,
	themeColor = 'cyan',
	pageSize = DEFAULT_PAGE_SIZE,
	showCheckbox = false,
	showLineNumbers = false,
	cursorSymbol = '-> ',
	checkedSymbol = '[x] ',
	uncheckedSymbol = '[ ] ',
	loading,
	loadingText,
	errorText,
	errorTitle,
	errorEnterAction = 'escape',
	statusText,
	escapeHint,
	leftHelpLines = [],
	rightHelpLines = [],
	onEscape,
	onEnter,
	onToggleCurrent,
	onToggleAll,
	onSwitchTable,
	onClearError,
}: TableBrowserProps) => {
	const [activeIndex, setActiveIndex] = useState(0);
	const [windowStart, setWindowStart] = useState(0);

	useEffect(() => {
		if (items.length === 0) {
			setActiveIndex(0);
			setWindowStart(0);
			return;
		}

		setActiveIndex(previous => Math.min(previous, items.length - 1));
		setWindowStart(previous => {
			const maxStart = Math.max(0, items.length - pageSize);
			return Math.min(previous, maxStart);
		});
	}, [items.length, pageSize]);

	const windowEnd = windowStart + pageSize;
	const visibleItems = useMemo(() => items.slice(windowStart, windowEnd), [items, windowStart, windowEnd]);
	const lineNumberWidth = Math.max(1, String(items.length).length);
	const helpRowCount = Math.max(leftHelpLines.length, rightHelpLines.length);

	useInput((input, key) => {
		if (key.escape) {
			onEscape();
			return;
		}

		if (loading || statusText) {
			return;
		}

		if (errorText) {
			if (key.return) {
				if (errorEnterAction === 'clear') {
					onClearError?.();
					return;
				}

				onEscape();
			}
			return;
		}

		if ((key.tab && key.shift) || input === '\u001b[Z') {
			onSwitchTable?.();
			setActiveIndex(0);
			setWindowStart(0);
			return;
		}

		if (items.length === 0) {
			if (key.return) {
				onEnter();
			}
			return;
		}

		if (key.upArrow) {
			setActiveIndex(previous => {
				const next = Math.max(0, previous - 1);
				if (next < windowStart) {
					setWindowStart(next);
				}
				return next;
			});
			return;
		}

		if (key.downArrow) {
			setActiveIndex(previous => {
				const next = Math.min(items.length - 1, previous + 1);
				if (next >= windowStart + pageSize) {
					setWindowStart(next - pageSize + 1);
				}
				return next;
			});
			return;
		}

		if (showCheckbox && input === ' ') {
			onToggleCurrent?.(activeIndex);
			return;
		}

		if (showCheckbox && input === 'A') {
			onToggleAll?.();
			return;
		}

		if (key.return) {
			onEnter();
		}
	});

	if (loading) {
		return (
			<Box flexDirection="column" paddingX={1} paddingTop={1}>
				<Text color={themeColor} bold>{loadingText}</Text>
				<Text color="gray">Press esc to {escapeHint}.</Text>
			</Box>
		);
	}

	if (errorText && errorEnterAction === 'escape') {
		return (
			<Box flexDirection="column" paddingX={1} paddingTop={1}>
				<Text color="red" bold>{errorTitle ?? 'Failed to load data'}</Text>
				<Text color="gray">{errorText}</Text>
				<Text color="gray">Press enter or esc to {escapeHint}.</Text>
			</Box>
		);
	}

	return (
		<Box flexDirection="column" paddingX={1} paddingTop={1}>
			<Text color={themeColor} bold>{title}</Text>
			{subtitle ? <Text color="gray">{subtitle}</Text> : null}

			<Box
				flexDirection="column"
				borderStyle="single"
				borderTop={true}
				borderBottom={true}
				borderLeft={false}
				borderRight={false}
				borderColor="gray"
				paddingY={0}
				marginY={0}
			>
				{visibleItems.map((item, visibleIndex) => {
					const absoluteIndex = windowStart + visibleIndex;
					const isCurrent = absoluteIndex === activeIndex;
					const lineNumber = String(absoluteIndex + 1).padStart(lineNumberWidth, ' ');
					return (
						<Box key={item.id} justifyContent="space-between">
							<Text color={isCurrent ? themeColor : 'white'} bold={isCurrent}>
								{isCurrent ? cursorSymbol : '   '}
								{showLineNumbers ? (
									<Text color="gray">{`${lineNumber}. `}</Text>
								) : null}
								{showCheckbox ? (
									<Text color={item.checked ? themeColor : 'gray'}>
										{item.checked ? checkedSymbol : uncheckedSymbol}
									</Text>
								) : null}
								{item.label}
							</Text>
							{item.rightText ? (
								<Text color={item.rightColor ?? 'gray'} dimColor>
									{item.rightText}
								</Text>
							) : null}
						</Box>
					);
				})}

				{visibleItems.length < pageSize &&
					Array.from({ length: pageSize - visibleItems.length }).map((_, index) => (
						<Box key={`empty-${index}`} height={1} />
					))}
			</Box>

			<Box flexDirection="column" marginTop={1}>
				{Array.from({ length: helpRowCount }).map((_, index) => (
					<Box key={`help-${index}`} justifyContent="space-between">
						<Text color="gray">{leftHelpLines[index] ?? ''}</Text>
						<Text color="gray">{rightHelpLines[index] ?? ''}</Text>
					</Box>
				))}
			</Box>

			{errorText ? (
				<Box marginTop={1}>
					<Text color="red" bold>{errorText}</Text>
				</Box>
			) : null}

			{statusText ? (
				<Box marginTop={1}>
					<Text color="green" bold>{statusText}</Text>
				</Box>
			) : null}
		</Box>
	);
};