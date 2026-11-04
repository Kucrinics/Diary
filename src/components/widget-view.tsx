"use client";

import type { AppState } from "@/lib/types";
import { addDays, currentWeek, formatRange, startOfWeek } from "@/lib/week";
import { WeekList } from "@/components/week-list";

function pad(n: number) { return String(n).padStart(2, "0"); }

// Компактный режим виджета (/?widget=1): только неделя ПН→ВС, автообновление.
export function WidgetView({ state, standalone }: { state: AppState; standalone: boolean }) {
  const now = new Date();
  const weekStart = startOfWeek(now);
  const days = currentWeek(state.tasks, state.members);
  const updated = new Date(state.serverTime);

  return (
    <div className="mx-auto min-h-dvh w-full max-w-md p-3">
      <header className="mb-2 flex items-end justify-between gap-2">
        <div>
          <h1 className="text-base leading-tight font-extrabold">Расписание</h1>
          <p className="text-[11px] text-muted-foreground">
            {formatRange(weekStart, addDays(weekStart, 6))} · ПН → ВС
          </p>
        </div>
        <p className="tnum text-[10px] text-muted-foreground">
          Обновлено {pad(updated.getHours())}:{pad(updated.getMinutes())}:{pad(updated.getSeconds())}
        </p>
      </header>

      <WeekList days={days} compact />

      {!standalone && (
        <footer className="mt-4 rounded-xl border border-dashed p-3 text-center">
          <p className="text-[11px] font-semibold">Закрепить виджет на экране</p>
          <p className="mt-1 text-[10px] text-muted-foreground">
            Android: меню ⋮ → «Добавить на главный экран»<br />
            iPhone: «Поделиться» → «На экран „Домой“»
          </p>
        </footer>
      )}
    </div>
  );
}
