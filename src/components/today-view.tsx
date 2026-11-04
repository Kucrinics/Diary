"use client";

import { CalendarDays, PartyPopper } from "lucide-react";
import type { AppState } from "@/lib/types";
import { buildWeek, dateKey, formatDayLong, startOfWeek } from "@/lib/week";
import { Card, CardContent } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { WeekList } from "@/components/week-list";

export function TodayView({ state, onToggle }: { state: AppState; onToggle: (id: string, dk: string, done: boolean) => void }) {
  const now = new Date();
  const days = buildWeek(state.tasks, state.members, startOfWeek(now));
  const today = days.find((d) => d.key === dateKey(now));

  const total = today?.items.length ?? 0;
  const done = today?.items.filter((i) => i.done).length ?? 0;
  const left = total - done;

  return (
    <div className="space-y-3">
      <Card>
        <CardContent className="p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-primary">Сегодня</p>
          <h2 className="mt-0.5 text-lg font-extrabold">{formatDayLong(now)}</h2>
          {total > 0 ? (
            <div className="mt-3 space-y-1.5">
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span>
                  {done} из {total} выполнено
                </span>
                <span className="font-semibold text-foreground">
                  {left === 0 ? "Всё сделано 🎉" : `Осталось ${left}`}
                </span>
              </div>
              <Progress value={total ? (done / total) * 100 : 0} className="h-2" />
            </div>
          ) : (
            <p className="mt-1 text-sm text-muted-foreground">Задач на сегодня нет</p>
          )}
        </CardContent>
      </Card>

      {total === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed py-10 text-center">
          <PartyPopper className="size-8 text-primary" aria-hidden />
          <p className="text-sm font-medium">Свободный день!</p>
          <p className="max-w-60 text-xs text-muted-foreground">
            Добавьте задачу кнопкой «＋» — она появится здесь и в виджете
          </p>
        </div>
      ) : (
        today && <WeekList days={[today]} onToggle={onToggle} />
      )}
    </div>
  );
}

export function TodayEmptyHint() {
  return (
    <div className="flex items-center gap-2 text-xs text-muted-foreground">
      <CalendarDays className="size-3.5" aria-hidden />
      Нажмите «＋», чтобы добавить задачу
    </div>
  );
}
