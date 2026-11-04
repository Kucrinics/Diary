// Работа с датами, неделей и сортировкой задач.
// Ключевой принцип (жалоба из отзыва «дни вразнобой»):
// дни недели ВСЕГДА выводятся в порядке ПН → ВС, внутри дня — по времени.

import type { Member, Task } from "./types";

export const WEEKDAYS_RU = [
  { iso: 1, short: "ПН", full: "Понедельник" },
  { iso: 2, short: "ВТ", full: "Вторник" },
  { iso: 3, short: "СР", full: "Среда" },
  { iso: 4, short: "ЧТ", full: "Четверг" },
  { iso: 5, short: "ПТ", full: "Пятница" },
  { iso: 6, short: "СБ", full: "Суббота" },
  { iso: 7, short: "ВС", full: "Воскресенье" },
] as const;

const MONTHS_GEN = [
  "января", "февраля", "марта", "апреля", "мая", "июня",
  "июля", "августа", "сентября", "октября", "ноября", "декабря",
];

const pad = (n: number) => String(n).padStart(2, "0");

export function parseHM(time: string): number {
  const [h, m] = time.split(":").map((x) => parseInt(x, 10));
  return (isNaN(h) ? 0 : h) * 60 + (isNaN(m) ? 0 : m);
}

export function dateKey(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function addDays(d: Date, n: number): Date {
  const r = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  r.setDate(r.getDate() + n);
  return r;
}

// Начало недели — понедельник
export function startOfWeek(d: Date): Date {
  const day = d.getDay(); // 0=ВС..6=СБ
  return addDays(d, -(day + 6) % 7);
}

export function weekdayDates(weekStart: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));
}

export function formatDayLong(d: Date): string {
  const wd = WEEKDAYS_RU[(d.getDay() + 6) % 7];
  return `${wd.full.toLowerCase().replace(/^./, (c) => c.toUpperCase())}, ${d.getDate()} ${MONTHS_GEN[d.getMonth()]}`;
}

export function formatDayShort(d: Date): string {
  return `${d.getDate()} ${MONTHS_GEN[d.getMonth()]}`;
}

export function formatRange(a: Date, b: Date): string {
  if (a.getMonth() === b.getMonth()) return `${a.getDate()}–${b.getDate()} ${MONTHS_GEN[a.getMonth()]}`;
  return `${a.getDate()} ${MONTHS_GEN[a.getMonth()]} – ${b.getDate()} ${MONTHS_GEN[b.getMonth()]}`;
}

// ISO-номер дня недели (1=ПН … 7=ВС)
export function isoWeekday(d: Date): number {
  return ((d.getDay() + 6) % 7) + 1;
}

export function weekdaySet(task: Task): Set<number> {
  return new Set(
    task.weekdays
      .split(",")
      .map((s) => parseInt(s.trim(), 10))
      .filter((n) => !isNaN(n) && n >= 1 && n <= 7),
  );
}

// Попадает ли задача на конкретный день
export function isTaskOnDay(task: Task, d: Date): boolean {
  if (task.weekdays) return weekdaySet(task).has(isoWeekday(d));
  if (task.date) return task.date === dateKey(d);
  return false;
}

export function taskRepeatLabel(task: Task): string {
  if (task.weekdays) {
    const set = weekdaySet(task);
    if (set.size === 7) return "Каждый день";
    return WEEKDAYS_RU.filter((w) => set.has(w.iso)).map((w) => w.short).join(", ");
  }
  if (task.date) {
    const [y, m, d] = task.date.split("-").map((x) => parseInt(x, 10));
    return `${d} ${MONTHS_GEN[m - 1]} ${y !== new Date().getFullYear() ? y : ""}`.trim();
  }
  return "Без повтора";
}

export type Occurrence = {
  task: Task;
  member: Member | null;
  dateKey: string;
  minutes: number;
  done: boolean;
};

export type DayGroup = {
  date: Date;
  key: string;
  weekdayIndex: number; // 0=ПН … 6=ВС
  items: Occurrence[];
};

// Разбор задач по дням недели. Строго: ПН → ВС, внутри дня по времени.
export function buildWeek(tasks: Task[], members: Member[], weekStart: Date): DayGroup[] {
  const byId = new Map(members.map((m) => [m.id, m]));
  return weekdayDates(weekStart).map((date, weekdayIndex) => {
    const key = dateKey(date);
    const items: Occurrence[] = tasks
      .filter((t) => isTaskOnDay(t, date))
      .map((task) => ({
        task,
        member: task.memberId ? byId.get(task.memberId) ?? null : null,
        dateKey: key,
        minutes: parseHM(task.time),
        done: task.doneDates.includes(key),
      }))
      .sort((a, b) => a.minutes - b.minutes || a.task.title.localeCompare(b.task.title, "ru"));
    return { date, key, weekdayIndex, items };
  });
}

export function currentWeek(tasks: Task[], members: Member[]): DayGroup[] {
  return buildWeek(tasks, members, startOfWeek(new Date()));
}
