// Палитра цветов участников (отзыв: «добавьте выделение цветом, хочу отличать детей»)

export const PALETTE: { hex: string; name: string }[] = [
  { hex: "#ec4899", name: "Розовый" },
  { hex: "#f97316", name: "Оранжевый" },
  { hex: "#f59e0b", name: "Янтарный" },
  { hex: "#84cc16", name: "Лаймовый" },
  { hex: "#22c55e", name: "Зелёный" },
  { hex: "#14b8a6", name: "Бирюзовый" },
  { hex: "#06b6d4", name: "Голубой" },
  { hex: "#8b5cf6", name: "Сиреневый" },
  { hex: "#d946ef", name: "Фуксия" },
  { hex: "#ef4444", name: "Красный" },
  { hex: "#eab308", name: "Солнечный" },
  { hex: "#a3a3a3", name: "Серый" },
];

// Полупрозрачный фон под цвет (для плашек)
export function tint(hex: string, alpha = "22"): string {
  return `${hex}${alpha}`;
}
