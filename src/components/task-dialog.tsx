"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Loader2, Pencil, Plus, Trash2, Volume2 } from "lucide-react";
import type { AppState, Task, TaskPayload } from "@/lib/types";
import { SOUND_OPTIONS, playSound, soundLabel } from "@/lib/sounds";
import { WEEKDAYS_RU } from "@/lib/week";
import { dateKey } from "@/lib/week";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

type FormState = {
  title: string;
  memberId: string; // "" = без участника
  type: "weekly" | "once";
  weekdays: number[];
  date: string;
  time: string;
  sound: string; // "" = как в настройках
  note: string;
};

function toForm(task: Task | null, defaultSound: string): FormState {
  if (!task) {
    return {
      title: "", memberId: "", type: "weekly", weekdays: [1],
      date: dateKey(new Date()), time: "09:00", sound: "", note: "",
    };
  }
  return {
    title: task.title,
    memberId: task.memberId ?? "",
    type: task.weekdays ? "weekly" : "once",
    weekdays: task.weekdays ? task.weekdays.split(",").map((n) => parseInt(n, 10)).filter((n) => n >= 1 && n <= 7) : [],
    date: task.date ?? dateKey(new Date()),
    time: task.time,
    sound: task.sound ?? "",
    note: task.note,
  };
}

function toPayload(f: FormState): TaskPayload {
  return {
    title: f.title,
    note: f.note,
    memberId: f.memberId || null,
    time: f.time,
    weekdays: f.type === "weekly" ? [...f.weekdays].sort((a, b) => a - b).join(",") : "",
    date: f.type === "once" ? f.date : null,
    sound: f.sound || null,
  };
}

export function TaskDialog({
  open, onOpenChange, task, state, onSave, onDelete,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  task: Task | null;
  state: AppState;
  onSave: (p: TaskPayload, id?: string) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}) {
  const [form, setForm] = useState<FormState>(() => toForm(task, state.settings.sound));
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (open) setForm(toForm(task, state.settings.sound));
  }, [open, task?.id]);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setForm((f) => ({ ...f, [k]: v }));

  const toggleWeekday = (iso: number) =>
    setForm((f) => ({
      ...f,
      weekdays: f.weekdays.includes(iso) ? f.weekdays.filter((d) => d !== iso) : [...f.weekdays, iso],
    }));

  async function submit() {
    if (!form.title.trim()) { toast.error("Введите название задачи"); return; }
    if (form.type === "weekly" && form.weekdays.length === 0) { toast.error("Выберите хотя бы один день недели"); return; }
    setPending(true);
    try {
      await onSave(toPayload(form), task?.id);
      toast.success(task ? "Изменения сохранены" : "Задача добавлена");
      onOpenChange(false);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Не удалось сохранить");
    } finally {
      setPending(false);
    }
  }

  const previewSound = () => playSound(form.sound || state.settings.sound);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{task ? "Редактировать задачу" : "Новая задача"}</DialogTitle>
          <DialogDescription>
            {task ? "Изменения сохраняются сразу после нажатия «Сохранить»" : "Выберите дни недели или конкретную дату"}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="task-title">Название</Label>
            <Input id="task-title" value={form.title} onChange={(e) => set("title", e.target.value)}
              placeholder="Например, Бассейн" maxLength={120} />
          </div>

          <div className="space-y-1.5">
            <Label>Участник</Label>
            <Select value={form.memberId || "none"} onValueChange={(v) => set("memberId", v === "none" ? "" : v)}>
              <SelectTrigger className="w-full"><SelectValue placeholder="Без участника" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="none">Без участника</SelectItem>
                {state.members.map((m) => (
                  <SelectItem key={m.id} value={m.id}>
                    <span className="flex items-center gap-2">
                      <span className="size-2.5 rounded-full" style={{ backgroundColor: m.color }} />
                      {m.name}
                    </span>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label>Повторение</Label>
            <RadioGroup value={form.type} onValueChange={(v) => set("type", v as "weekly" | "once")} className="flex gap-4">
              <label className="flex items-center gap-2 text-sm font-medium">
                <RadioGroupItem value="weekly" /> Каждую неделю
              </label>
              <label className="flex items-center gap-2 text-sm font-medium">
                <RadioGroupItem value="once" /> Один раз
              </label>
            </RadioGroup>
          </div>

          {form.type === "weekly" ? (
            <div className="space-y-1.5">
              <Label>Дни недели</Label>
              <div className="grid grid-cols-7 gap-1">
                {WEEKDAYS_RU.map((w) => (
                  <button
                    key={w.iso}
                    type="button"
                    onClick={() => toggleWeekday(w.iso)}
                    aria-pressed={form.weekdays.includes(w.iso)}
                    aria-label={w.full}
                    className={cn(
                      "rounded-lg py-2 text-[11px] font-extrabold transition-colors",
                      form.weekdays.includes(w.iso)
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-muted-foreground hover:bg-accent",
                    )}
                  >
                    {w.short}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="space-y-1.5">
              <Label htmlFor="task-date">Дата</Label>
              <Input id="task-date" type="date" value={form.date} onChange={(e) => set("date", e.target.value)} />
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="task-time">Время</Label>
              <Input id="task-time" type="time" value={form.time} onChange={(e) => set("time", e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>Свой звук</Label>
              <div className="flex gap-1.5">
                <Select value={form.sound || "none"} onValueChange={(v) => set("sound", v === "none" ? "" : v)}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">Как в настройках</SelectItem>
                    {SOUND_OPTIONS.map((s) => (
                      <SelectItem key={s.id} value={s.id}>{s.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button type="button" variant="outline" size="icon" className="size-9 shrink-0" onClick={previewSound}
                  aria-label="Прослушать звук">
                  <Volume2 className="size-4" />
                </Button>
              </div>
              <p className="text-[11px] text-muted-foreground">
                Будет: {soundLabel(form.sound || state.settings.sound)}
              </p>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="task-note">Заметка</Label>
            <Textarea id="task-note" value={form.note} onChange={(e) => set("note", e.target.value)}
              placeholder="Что не забыть" rows={2} maxLength={2000} />
          </div>
        </div>

        <DialogFooter className="mt-2 items-center gap-2 sm:justify-between">
          {task ? (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive">
                  <Trash2 className="size-4" /> Удалить
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Удалить задачу?</AlertDialogTitle>
                  <AlertDialogDescription>
                    «{task.title}» будет удалена из расписания. Это действие нельзя отменить.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Отмена</AlertDialogCancel>
                  <AlertDialogAction
                    className="bg-destructive text-white hover:bg-destructive/90"
                    onClick={async () => { await onDelete(task.id); toast.success("Задача удалена"); onOpenChange(false); }}
                  >
                    Удалить
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          ) : (
            <span />
          )}
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => onOpenChange(false)}>Отмена</Button>
            <Button onClick={submit} disabled={pending}>
              {pending ? <Loader2 className="size-4 animate-spin" /> : task ? <Pencil className="size-4" /> : <Plus className="size-4" />}
              Сохранить
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
