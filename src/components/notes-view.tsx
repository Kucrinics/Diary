"use client";

import { useState } from "react";
import { toast } from "sonner";
import {
  Archive, ChevronDown, Loader2, Pencil, Plus, Trash2, Undo2, X,
} from "lucide-react";
import type { AppState, Note } from "@/lib/types";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Collapsible, CollapsibleContent, CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

function updatedLabel(iso: string): string {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()}, ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function NotesView({
  state, onCreate, onUpdate, onDeleteForever, onEmptyTrash,
}: {
  state: AppState;
  onCreate: (p: { title: string; content: string }) => Promise<void>;
  onUpdate: (id: string, p: { title?: string; content?: string; trashed?: boolean }) => Promise<void>;
  onDeleteForever: (id: string) => Promise<void>;
  onEmptyTrash: () => Promise<void>;
}) {
  const active = state.notes.filter((n) => !n.trashed);
  const trashed = state.notes.filter((n) => n.trashed);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<Note | null>(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [pending, setPending] = useState(false);
  const [trashOpen, setTrashOpen] = useState(false);

  const openNew = () => { setEditing(null); setTitle(""); setContent(""); setEditorOpen(true); };
  const openEdit = (n: Note) => { setEditing(n); setTitle(n.title); setContent(n.content); setEditorOpen(true); };

  async function save() {
    if (!title.trim()) { toast.error("Введите заголовок заметки"); return; }
    setPending(true);
    try {
      if (editing) await onUpdate(editing.id, { title, content });
      else await onCreate({ title, content });
      toast.success(editing ? "Заметка сохранена" : "Заметка создана");
      setEditorOpen(false);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Не удалось сохранить");
    } finally {
      setPending(false);
    }
  }

  function moveToTrash(n: Note) {
    void onUpdate(n.id, { trashed: true }).then(() => {
      toast("Заметка в корзине", {
        description: `«${n.title}» можно восстановить`,
        action: { label: "Отменить", onClick: () => void onUpdate(n.id, { trashed: false }) },
      });
    });
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between px-1">
        <h3 className="text-sm font-bold">Мои заметки</h3>
        <Button size="sm" onClick={openNew}>
          <Plus className="size-4" /> Новая
        </Button>
      </div>

      {active.length === 0 ? (
        <p className="rounded-xl border border-dashed px-4 py-8 text-center text-xs text-muted-foreground">
          Заметок пока нет — создайте первую
        </p>
      ) : (
        <div className="grid gap-2.5 sm:grid-cols-2">
          {active.map((n) => (
            <Card key={n.id} className="py-0">
              <CardContent className="p-3.5">
                <p className="text-sm font-semibold">{n.title}</p>
                {n.content && (
                  <p className="mt-1 whitespace-pre-wrap text-xs leading-relaxed text-muted-foreground line-clamp-4">
                    {n.content}
                  </p>
                )}
                <div className="mt-2.5 flex items-center gap-1">
                  <span className="mr-auto text-[10px] text-muted-foreground">{updatedLabel(n.updatedAt)}</span>
                  <Button variant="ghost" size="icon" className="size-8" onClick={() => openEdit(n)} aria-label="Редактировать заметку">
                    <Pencil className="size-3.5" />
                  </Button>
                  {/* Иконка удаления в корзину — одна, без дублей (исправлено по отзыву) */}
                  <Button
                    variant="ghost" size="icon"
                    className="size-8 text-destructive hover:text-destructive"
                    onClick={() => moveToTrash(n)}
                    aria-label="Удалить заметку в корзину"
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Collapsible open={trashOpen} onOpenChange={setTrashOpen}>
        <CollapsibleTrigger asChild>
          <Button variant="outline" className="w-full justify-between">
            <span className="flex items-center gap-2">
              <Archive className="size-4" /> Корзина
              <Badge variant="secondary">{trashed.length}</Badge>
            </span>
            <ChevronDown className={cn("size-4 transition-transform", trashOpen && "rotate-180")} />
          </Button>
        </CollapsibleTrigger>
        <CollapsibleContent className="pt-2.5">
          {trashed.length === 0 ? (
            <p className="px-1 text-xs text-muted-foreground">Корзина пуста</p>
          ) : (
            <div className="space-y-2">
              {trashed.map((n) => (
                <Card key={n.id} className="py-0 border-dashed">
                  <CardContent className="flex items-center gap-2 p-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-muted-foreground">{n.title}</p>
                      <p className="text-[10px] text-muted-foreground">{updatedLabel(n.updatedAt)}</p>
                    </div>
                    <Button variant="ghost" size="icon" className="size-8" onClick={() => void onUpdate(n.id, { trashed: false })} aria-label="Восстановить">
                      <Undo2 className="size-3.5" />
                    </Button>
                    <AlertDialog>
                      <AlertDialogTrigger asChild>
                        <Button variant="ghost" size="icon" className="size-8 text-destructive hover:text-destructive" aria-label="Удалить навсегда">
                          <X className="size-3.5" />
                        </Button>
                      </AlertDialogTrigger>
                      <AlertDialogContent>
                        <AlertDialogHeader>
                          <AlertDialogTitle>Удалить навсегда?</AlertDialogTitle>
                          <AlertDialogDescription>Заметка «{n.title}» будет удалена безвозвратно.</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                          <AlertDialogCancel>Отмена</AlertDialogCancel>
                          <AlertDialogAction
                            className="bg-destructive text-white hover:bg-destructive/90"
                            onClick={async () => { await onDeleteForever(n.id); toast.success("Заметка удалена навсегда"); }}
                          >
                            Удалить
                          </AlertDialogAction>
                        </AlertDialogFooter>
                      </AlertDialogContent>
                    </AlertDialog>
                  </CardContent>
                </Card>
              ))}
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="outline" size="sm" className="text-destructive hover:text-destructive">
                    <Trash2 className="size-3.5" /> Очистить корзину
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Очистить корзину?</AlertDialogTitle>
                    <AlertDialogDescription>Все заметки в корзине ({trashed.length}) будут удалены безвозвратно.</AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Отмена</AlertDialogCancel>
                    <AlertDialogAction
                      className="bg-destructive text-white hover:bg-destructive/90"
                      onClick={async () => { await onEmptyTrash(); toast.success("Корзина очищена"); }}
                    >
                      Очистить
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>
          )}
        </CollapsibleContent>
      </Collapsible>

      <Dialog open={editorOpen} onOpenChange={setEditorOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editing ? "Редактировать заметку" : "Новая заметка"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="note-title">Заголовок</Label>
              <Input id="note-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={120} placeholder="Например, Контакты" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="note-content">Текст</Label>
              <Textarea id="note-content" value={content} onChange={(e) => setContent(e.target.value)} rows={6} placeholder="Любые списки, телефоны, напоминания" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditorOpen(false)}>Отмена</Button>
            <Button onClick={save} disabled={pending}>
              {pending && <Loader2 className="size-4 animate-spin" />} Сохранить
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
