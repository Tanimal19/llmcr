import { CommandProps } from '../types.js';
import { DbBrowser } from '../components/dbbrowser.js';

export const LsDbCommand = ({ onBack, oneShotArgs }: CommandProps) => {
	return <DbBrowser editMode={false} onBack={onBack} oneShotArgs={oneShotArgs === true} />;
};
