import { useEffect, useMemo, useState } from 'react';
import { getRagScope, setRagScope } from '../api.js';
import { TableBrowser, type TableBrowserItem } from '../components/tableBrowser.js';
import { CommandProps } from '../types.js';

function toLabel(path: string): string {
	const segments = path.split(/[/\\]/);
	return segments.at(-1) ?? path;
}

export const SetRagCommand = ({ onBack }: CommandProps) => {

	const [items, setItems] = useState<TableBrowserItem[]>([]);
	const [isLoading, setIsLoading] = useState(true);
	const [isSaving, setIsSaving] = useState(false);
	const [errorMsg, setErrorMsg] = useState<string | undefined>(undefined);
	const [statusMsg, setStatusMsg] = useState<string | undefined>(undefined);

	const selectedCount = useMemo(() => items.filter(item => item.checked).length, [items]);

	const leave = () => {
		onBack();
	};

	useEffect(() => {
		let alive = true;

		(async () => {
			try {
				const scopeMap = await getRagScope();
				if (!alive) {
					return;
				}

				const nextItems = Object.entries(scopeMap)
					.map(([path, checked]) => ({
						id: path,
						label: toLabel(path),
						checked,
						rightText: path,
					}))
					.sort((a, b) => a.id.localeCompare(b.id));

				setItems(nextItems);
			} catch (error) {
				if (!alive) {
					return;
				}

				setErrorMsg(error instanceof Error ? error.message : String(error));
			} finally {
				if (alive) {
					setIsLoading(false);
				}
			}
		})();

		return () => {
			alive = false;
		};
	}, []);

	const saveScope = async () => {
		if (isSaving || isLoading) {
			return;
		}

		const selectedPaths = items.filter(item => item.checked).map(item => item.id);
		if (selectedPaths.length === 0) {
			setErrorMsg('At least one track root must be selected.');
			return;
		}

		setIsSaving(true);
		setErrorMsg(undefined);

		try {
      setStatusMsg(`Saved ${selectedPaths.length} track root(s)...`);
			await setRagScope(selectedPaths);
			setStatusMsg(`Complete. Going back...`);
			setTimeout(leave, 700);
		} catch (error) {
			setErrorMsg(error instanceof Error ? error.message : String(error));
		} finally {
			setIsSaving(false);
		}
	};

	return (
		<TableBrowser
			title="RAG Scope"
			subtitle={`Selected: ${selectedCount}/${items.length}`}
			items={items}
			showCheckbox={true}
			loading={isLoading}
			loadingText="Loading RAG scope..."
			errorText={errorMsg}
			errorEnterAction="clear"
			statusText={statusMsg}
			escapeHint={'back'}
			leftHelpLines={[
				'up/down move',
				'space toggle',
				'shift+A toggle all',
			]}
			rightHelpLines={[
				'enter save',
				'esc back',
			]}
			onEscape={leave}
			onEnter={() => {
				void saveScope();
			}}
			onToggleCurrent={index => {
				setItems(previous => previous.map((item, itemIndex) => (
					itemIndex === index ? { ...item, checked: !item.checked } : item
				)));
			}}
			onToggleAll={() => {
				const isAllChecked = items.every(item => item.checked);
				setItems(previous => previous.map(item => ({
					...item,
					checked: !isAllChecked,
				})));
			}}
			onClearError={() => {
				setErrorMsg(undefined);
			}}
		/>
	);
};
