"use client";

import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { AppState } from "@/lib/types";
import { addDays, buildWeek, formatRange, startOfWeek } from "@/lib/week";
import { Button } from "@/components/ui/button";
import { WeekList } from "@/components/week-list";

export function WeekView({ state, onToggle }: { state: AppState; onToggle: (id: string, dk: string, done: boolean) => void }) {
  const [offset, setOffset] = useState(0);
  const weekStart = addDays(startOfWeek(new Date()), offset * 7);
  const days = buildWeek(state.tasks, state.members, weekStart);

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-1">
        <Button variant="outline" size="icon" className="size-9" onClick={() => setOffset((o) => o - 1)} aria-label="Прошлая неделя">
          <ChevronLeft className="size-4" />
        </Button>
        <div className="flex-1 text-center">
          <p className="text-sm font-extrabold">{formatRange(weekStart, addDays(weekStart, 6))}</p>
          {offset !== 0 && (
            <button className="text-[11px] text-primary underline-offset-2 hover:underline" onClick={() => setOffset(0)}>
              вернуться к текущей неделе
            </button>
          )}
        </div>
        <Button variant="outline" size="icon" className="size-9" onClick={() => setOffset((o) => o + 1)} aria-label="Следующая неделя">
          <ChevronRight className="size-4" />
        </Button>
      </div>

      <WeekList days={days} onToggle={onToggle} />
    </div>
  );
}
