// 定義傳遞給指令組件的標準 Props
export interface CommandProps {
	// 互動模式下，用來返回主選單的 callback
	onBack: () => void;
	// 單次執行模式下傳入的參數 (如果是 boolean flag 則為 true，string flag 則為字串)
	oneShotArgs?: string | boolean;
}
