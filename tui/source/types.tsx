// 定義傳遞給指令組件的標準 Props
export interface CommandProps {
  // 互動模式下，用來返回主選單的 callback
  onBack: () => void;
}
