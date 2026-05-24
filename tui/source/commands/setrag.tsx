import { CommandProps } from '../types.js';
import { DbBrowser } from '../components/dbbrowser.js';

export const SetRagCommand = ({ onBack, oneShotArgs }: CommandProps) => {
	return <DbBrowser editMode={true} onBack={onBack} oneShotArgs={oneShotArgs === true} />;
};
