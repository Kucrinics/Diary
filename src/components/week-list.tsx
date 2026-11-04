"use client";

import { WEEKDAYS_RU, dateKey, formatDayShort, type DayGroup } from "@/lib/week";
import { cn } from "@/lib/utils";
import { Checkbox } from "@/components/ui/checkbox";

type Props = {
  days: DayGroup[];
  compact?: boolean;
  onToggle?: (taskId: string, dateKey: string, done: boolean) => void;
};

// Список недели. Дни всегда идут строго ПН → ВС (индекс в массиве WEEKDAYS_RU),
// внутри дня задачи отсортированы по времени — buildWeek() в lib/week.ts.
export function WeekList({ days, compact = false, onToggle }: Props) {
  const today = dateKey(new Date());

  return (
    <div className={cn("space-y-2", compact && "space-y-1.5")}>
      {days.map((day) => {
        const isToday = day.key === today;
        return (
          <section
            key={day.key}
            aria-label={WEEKDAYS_RU[day.weekdayIndex].full}
            className={cn(
              "overflow-hidden rounded-xl border bg-card",
              compact && "rounded-lg",
              isToday && "border-primary/50 bg-primary/[0.06]",
            )}
          >
            <header
              className={cn(
                "flex items-center gap-2 px-3 pt-2.5 pb-1.5",
                compact && "px-2.5 pt-2 pb-1",
              )}
            >
              <span
                className={cn(
                  "rounded-md px-1.5 py-0.5 text-[11px] font-extrabold tracking-wide",
                  isToday ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground",
                )}
              >
                {WEEKDAYS_RU[day.weekdayIndex].short}
              </span>
              <span className={cn("font-semibold", compact ? "text-xs" : "text-sm")}>
                {formatDayShort(day.date)}
              </span>
              {isToday && (
                <span className="text-[10px] font-bold text-primary uppercase tracking-wide">Сегодня</span>
              )}
              <span className="ml-auto text-[11px] text-muted-foreground">
                {day.items.length === 0 ? "нет задач" : `${day.items.length} задач${day.items.length === 1 ? "а" : ""}`}
              </span>
            </header>

            {day.items.length > 0 && (
              <ul className={cn("divide-y divide-border/50 pb-1.5", compact && "pb-1")}>
                {day.items.map((occ) => (
                  <li key={`${occ.task.id}-${day.key}`} className="flex items-center gap-2.5 px-3 py-2">
                    <span
                      className={cn(
                        "shrink-0 font-semibold tnum",
                        compact ? "w-8 text-[11px]" : "w-11 text-sm",
                      )}
                    >
                      {occ.task.time}
                    </span>
                    <span
                      aria-hidden
                      className={cn("shrink-0 rounded-full", compact ? "size-2" : "size-2.5")}
                      style={{
                        backgroundColor: occ.member?.color ?? "transparent",
                        boxShadow: occ.member ? undefined : "inset 0 0 0 1.5px var(--border)",
                      }}
                    />
                    <span className="min-w-0 flex-1">
                      <span
                        className={cn(
                          "block truncate font-medium",
                          compact ? "text-xs" : "text-sm",
                          occ.done && "text-muted-foreground line-through",
                        )}
                      >
                        {occ.task.title}
                      </span>
                      {!compact && occ.member && (
                        <span className="block truncate text-xs font-medium" style={{ color: occ.member.color }}>
                          {occ.member.name}
                        </span>
                      )}
                    </span>
                    {onToggle && (
                      <Checkbox
                        checked={occ.done}
                        onCheckedChange={(v) => onToggle(occ.task.id, occ.dateKey, v === true)}
                        aria-label={occ.done ? "Снять выполнение" : "Отметить выполненной"}
                        className="shrink-0"
                      />
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        );
      })}
    </div>
  );
}
