"use client";

import { toast } from "sonner";
import { CalendarClock, Pencil, Repeat2, Trash2, Volume2 } from "lucide-react";
import type { AppState, Task } from "@/lib/types";
import { taskRepeatLabel } from "@/lib/week";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

export function TasksView({
  state, onEdit, onDelete,
}: {
  state: AppState;
  onEdit: (task: Task) => void;
  onDelete: (id: string) => Promise<void>;
}) {
  const memberById = new Map(state.members.map((m) => [m.id, m]));
  const weekly = state.tasks.filter((t) => t.weekdays);
  const once = state.tasks.filter((t) => !t.weekdays);

  const Row = ({ task }: { task: Task }) => {
    const member = task.memberId ? memberById.get(task.memberId) ?? null : null;
    return (
      <Card className="py-0">
        <CardContent className="flex items-center gap-3 p-3.5">
          <span
            aria-hidden
            className="size-3 shrink-0 rounded-full"
            style={{ backgroundColor: member?.color ?? "transparent", boxShadow: member ? undefined : "inset 0 0 0 1.5px var(--border)" }}
          />
          <button className="min-w-0 flex-1 text-left" onClick={() => onEdit(task)}>
            <span className="block truncate text-sm font-semibold">{task.title}</span>
            <span className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted-foreground">
              <span className="tnum font-semibold text-foreground/80">{task.time}</span>
              <span>{task.weekdays ? <Repeat2 className="mr-0.5 inline size-3" aria-hidden /> : <CalendarClock className="mr-0.5 inline size-3" aria-hidden />}{taskRepeatLabel(task)}</span>
              {member && <span className="font-medium" style={{ color: member.color }}>{member.name}</span>}
              {task.sound && <span className="inline-flex items-center gap-0.5"><Volume2 className="size-3" aria-hidden />{task.sound}</span>}
            </span>
          </button>
          <div className="flex shrink-0 gap-0.5">
            <Button variant="ghost" size="icon" className="size-9" onClick={() => onEdit(task)} aria-label="Редактировать">
              <Pencil className="size-4" />
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="ghost" size="icon" className="size-9 text-destructive hover:text-destructive" aria-label="Удалить задачу">
                  <Trash2 className="size-4" />
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Удалить задачу?</AlertDialogTitle>
                  <AlertDialogDescription>«{task.title}» будет удалена из расписания. Это действие нельзя отменить.</AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Отмена</AlertDialogCancel>
                  <AlertDialogAction
                    className="bg-destructive text-white hover:bg-destructive/90"
                    onClick={async () => { await onDelete(task.id); toast.success("Задача удалена"); }}
                  >
                    Удалить
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </CardContent>
      </Card>
    );
  };

  return (
    <div className="space-y-4">
      <section className="space-y-2">
        <h3 className="flex items-center gap-2 px-1 text-sm font-bold">
          Еженедельные <Badge variant="secondary">{weekly.length}</Badge>
        </h3>
        {weekly.length === 0 ? (
          <p className="rounded-xl border border-dashed px-4 py-6 text-center text-xs text-muted-foreground">
            Нет еженедельных задач
          </p>
        ) : (
          weekly.map((t) => <Row key={t.id} task={t} />)
        )}
      </section>

      <section className="space-y-2">
        <h3 className="flex items-center gap-2 px-1 text-sm font-bold">
          Разовые <Badge variant="secondary">{once.length}</Badge>
        </h3>
        {once.length === 0 ? (
          <p className="rounded-xl border border-dashed px-4 py-6 text-center text-xs text-muted-foreground">
            Нет разовых задач
          </p>
        ) : (
          once.map((t) => <Row key={t.id} task={t} />)
        )}
      </section>
    </div>
  );
}
