"use client";

import { useState, useSyncExternalStore } from "react";
import { useTheme } from "next-themes";
import {
  CalendarDays, ListChecks, Loader2, Moon, Plus, RefreshCcw, Settings as SettingsIcon,
  StickyNote, Sun,
} from "lucide-react";
import { useAppState, useMutations } from "@/hooks/use-app-state";
import { useReminders } from "@/hooks/use-reminders";
import { toast } from "sonner";
import type { Task, TaskPayload } from "@/lib/types";
import { formatDayLong } from "@/lib/week";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { NotesView } from "@/components/notes-view";
import { SettingsSheet } from "@/components/settings-sheet";
import { TaskDialog } from "@/components/task-dialog";
import { TasksView } from "@/components/tasks-view";
import { TodayView } from "@/components/today-view";
import { WeekView } from "@/components/week-view";
import { WidgetView } from "@/components/widget-view";

type Tab = "today" | "week" | "tasks" | "notes";

const TABS: { id: Tab; label: string; icon: typeof Sun }[] = [
  { id: "today", label: "Сегодня", icon: Sun },
  { id: "week", label: "Неделя", icon: CalendarDays },
  { id: "tasks", label: "Задачи", icon: ListChecks },
  { id: "notes", label: "Заметки", icon: StickyNote },
];

function subscribeNoop(_cb: () => void) {
  return () => undefined;
}

function subscribeUrl(cb: () => void) {
  window.addEventListener("popstate", cb);
  return () => window.removeEventListener("popstate", cb);
}

function subscribeStandalone(cb: () => void) {
  const mq = window.matchMedia("(display-mode: standalone)");
  mq.addEventListener("change", cb);
  return () => mq.removeEventListener("change", cb);
}

export function App() {
  // hydrated: false на сервере, true на клиенте — без setState в эффекте
  const mounted = useSyncExternalStore(subscribeNoop, () => true, () => false);
  const [tab, setTab] = useState<Tab>("today");
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [taskDialogOpen, setTaskDialogOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<Task | null>(null);

  // Режим виджета: /?widget=1 (его открывает иконка на главном экране телефона)
  const widget = useSyncExternalStore(subscribeUrl, () => new URLSearchParams(window.location.search).has("widget"), () => false);
  const standalone = useSyncExternalStore(subscribeStandalone, () => window.matchMedia("(display-mode: standalone)").matches, () => false);

  const { data: state, isLoading, isError, refetch, isFetching } = useAppState(widget ? 30_000 : 60_000);
  useReminders(state);
  const m = useMutations();

  const { resolvedTheme, setTheme } = useTheme();

  // Плавное автообновление: не мигаем скелетоном на фоновых обновлениях
  if (!mounted || (isLoading && !state)) {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-3 bg-background text-foreground">
        <Loader2 className="size-7 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">Загружаем расписание…</p>
      </div>
    );
  }

  if (isError || !state) {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-3 bg-background px-6 text-center text-foreground">
        <p className="text-sm font-semibold">Не удалось загрузить данные</p>
        <Button variant="outline" onClick={() => void refetch()}>
          <RefreshCcw className="size-4" /> Обновить
        </Button>
      </div>
    );
  }

  if (widget) return <WidgetView state={state} standalone={standalone} />;

  const openNewTask = () => { setEditingTask(null); setTaskDialogOpen(true); };
  const openEditTask = (t: Task) => { setEditingTask(t); setTaskDialogOpen(true); };

  async function saveTask(p: TaskPayload, id?: string) {
    if (id) await m.updateTask(id, p);
    else await m.createTask(p);
  }

  const toggle = (id: string, dk: string, done: boolean) => {
    void m.toggleTask(id, dk);
    if (done) toast.success("Выполнено ✓");
  };

  const showFab = tab !== "notes";

  return (
    <div className="min-h-dvh bg-background text-foreground">
      {/* Шапка */}
      <header className="sticky top-0 z-40 border-b border-border/60 bg-background/85 backdrop-blur">
        <div className="mx-auto flex h-14 w-full max-w-lg items-center gap-1.5 px-4">
          <div className="mr-auto min-w-0">
            <h1 className="truncate text-base leading-tight font-extrabold">Семейное расписание</h1>
            <p className="text-[11px] text-muted-foreground">{formatDayLong(new Date())}</p>
          </div>
          <Button
            variant="ghost" size="icon" className="size-9"
            onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
            aria-label={resolvedTheme === "dark" ? "Включить светлую тему" : "Включить тёмную тему"}
          >
            {mounted && resolvedTheme === "dark" ? <Sun className="size-4.5" /> : <Moon className="size-4.5" />}
          </Button>
          <Button variant="ghost" size="icon" className="size-9" onClick={() => setSettingsOpen(true)} aria-label="Настройки">
            <SettingsIcon className="size-4.5" />
          </Button>
        </div>
      </header>

      {/* Контент */}
      <main className="mx-auto w-full max-w-lg px-4 pt-4 pb-32">
        {tab === "today" && <TodayView state={state} onToggle={toggle} />}
        {tab === "week" && <WeekView state={state} onToggle={toggle} />}
        {tab === "tasks" && <TasksView state={state} onEdit={openEditTask} onDelete={m.deleteTask} />}
        {tab === "notes" && (
          <NotesView
            state={state}
            onCreate={m.createNote}
            onUpdate={m.updateNote}
            onDeleteForever={m.deleteNoteForever}
            onEmptyTrash={m.emptyTrash}
          />
        )}
      </main>

      {/* Кнопка добавления задачи */}
      {showFab && (
        <Button
          onClick={openNewTask}
          size="icon"
          className="fixed right-4 bottom-24 z-40 size-14 rounded-full shadow-lg shadow-primary/30 sm:right-[max(1rem,calc(50%-15rem))]"
          aria-label="Добавить задачу"
        >
          <Plus className="size-6" />
        </Button>
      )}

      {/* Нижняя навигация */}
      <nav
        aria-label="Основная навигация"
        className="fixed inset-x-0 bottom-0 z-40 border-t border-border/60 bg-background/95 backdrop-blur pb-[env(safe-area-inset-bottom)]"
      >
        <div className="mx-auto grid w-full max-w-lg grid-cols-4">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              aria-current={tab === t.id ? "page" : undefined}
              className={cn(
                "flex min-h-16 flex-col items-center justify-center gap-0.5 py-2 text-[10px] font-semibold transition-colors",
                tab === t.id ? "text-primary" : "text-muted-foreground hover:text-foreground",
              )}
            >
              <t.icon className="size-5" aria-hidden />
              {t.label}
            </button>
          ))}
        </div>
      </nav>

      {/* Диалоги и настройки */}
      <TaskDialog
        open={taskDialogOpen}
        onOpenChange={setTaskDialogOpen}
        task={editingTask}
        state={state}
        onSave={saveTask}
        onDelete={m.deleteTask}
      />
      <SettingsSheet
        open={settingsOpen}
        onOpenChange={setSettingsOpen}
        state={state}
        onSaveSettings={m.saveSettings}
        onCreateMember={m.createMember}
        onUpdateMember={m.updateMember}
        onDeleteMember={m.deleteMember}
      />

      {/* Скрытый индикатор фоновой синхронизации */}
      <span
        aria-hidden
        className={cn(
          "pointer-events-none fixed top-3.5 left-1/2 z-50 -translate-x-1/2 transition-opacity duration-300",
          isFetching ? "opacity-100" : "opacity-0",
        )}
      >
        <Loader2 className="size-3.5 animate-spin text-muted-foreground" />
      </span>
    </div>
  );
}
