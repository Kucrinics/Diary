"use client";

import { useState } from "react";
import { useTheme } from "next-themes";
import { toast } from "sonner";
import {
  BellRing, Loader2, Monitor, Moon, Plus, Sun, TabletSmartphone, Trash2, Volume2,
} from "lucide-react";
import type { AppState } from "@/lib/types";
import { PALETTE } from "@/lib/palette";
import { SOUND_OPTIONS, playSound } from "@/lib/sounds";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";

export function SettingsSheet({
  open, onOpenChange, state, onSaveSettings, onCreateMember, onUpdateMember, onDeleteMember,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  state: AppState;
  onSaveSettings: (p: { sound?: string; reminders?: boolean }) => Promise<void>;
  onCreateMember: (p: { name: string; color: string }) => Promise<void>;
  onUpdateMember: (id: string, p: { name?: string; color?: string }) => Promise<void>;
  onDeleteMember: (id: string) => Promise<void>;
}) {
  const { theme, setTheme } = useTheme();
  const [adding, setAdding] = useState(false);
  const [permission, setPermission] = useState<NotificationPermission | "unsupported">(
    typeof Notification === "undefined" ? "unsupported" : Notification.permission,
  );

  const usedColors = new Set(state.members.map((m) => m.color));
  const nextColor = PALETTE.find((c) => !usedColors.has(c.hex))?.hex ?? PALETTE[Math.floor(Math.random() * PALETTE.length)].hex;

  async function requestPermission() {
    if (typeof Notification === "undefined") return;
    const res = await Notification.requestPermission();
    setPermission(res);
    toast[res === "granted" ? "success" : "error"](
      res === "granted" ? "Уведомления разрешены" : "Уведомления не разрешены",
    );
  }

  const themeOptions = [
    { id: "light", label: "Светлая", icon: Sun },
    { id: "dark", label: "Тёмная", icon: Moon },
    { id: "system", label: "Системная", icon: Monitor },
  ];

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-sm">
        <SheetHeader>
          <SheetTitle>Настройки</SheetTitle>
          <SheetDescription>Участники, звуки, тема и виджет</SheetDescription>
        </SheetHeader>

        <div className="space-y-5 px-4 pb-8">
          {/* Участники и цвета */}
          <section className="space-y-2.5">
            <h3 className="text-sm font-bold">Участники и цвета</h3>
            <p className="text-xs text-muted-foreground">Цвет помогает отличать задачи детей в расписании и виджете</p>
            {state.members.map((m) => (
              <div key={m.id} className="flex items-center gap-2">
                <Popover>
                  <PopoverTrigger asChild>
                    <button
                      className="size-7 shrink-0 rounded-full ring-2 ring-offset-2 ring-offset-background transition-transform hover:scale-110"
                      style={{ backgroundColor: m.color }}
                      aria-label={`Цвет участника ${m.name}`}
                    />
                  </PopoverTrigger>
                  <PopoverContent className="w-52 p-2.5" side="left">
                    <div className="grid grid-cols-6 gap-1.5">
                      {PALETTE.map((c) => (
                        <button
                          key={c.hex}
                          title={c.name}
                          aria-label={c.name}
                          onClick={() => void onUpdateMember(m.id, { color: c.hex })}
                          className={cn(
                            "size-7 rounded-full transition-transform hover:scale-110",
                            c.hex === m.color && "ring-2 ring-primary ring-offset-2 ring-offset-background",
                          )}
                          style={{ backgroundColor: c.hex }}
                        />
                      ))}
                    </div>
                  </PopoverContent>
                </Popover>
                <Input
                  key={m.id}
                  defaultValue={m.name}
                  maxLength={30}
                  className="h-9"
                  aria-label={`Имя участника ${m.name}`}
                  onBlur={(e) => {
                    const name = e.target.value.trim();
                    if (name && name !== m.name) void onUpdateMember(m.id, { name });
                  }}
                  onKeyDown={(e) => { if (e.key === "Enter") (e.target as HTMLInputElement).blur(); }}
                />
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button variant="ghost" size="icon" className="size-9 shrink-0 text-destructive hover:text-destructive" aria-label={`Удалить ${m.name}`}>
                      <Trash2 className="size-4" />
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Удалить участника «{m.name}»?</AlertDialogTitle>
                      <AlertDialogDescription>
                        Его задачи останутся в расписании, но без цвета участника.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>Отмена</AlertDialogCancel>
                      <AlertDialogAction
                        className="bg-destructive text-white hover:bg-destructive/90"
                        onClick={async () => { await onDeleteMember(m.id); toast.success("Участник удалён"); }}
                      >
                        Удалить
                      </AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              </div>
            ))}

            {adding ? (
              <div className="flex items-center gap-2">
                <span aria-hidden className="size-7 shrink-0 rounded-full" style={{ backgroundColor: nextColor }} />
                <Input
                  autoFocus
                  placeholder="Имя участника"
                  maxLength={30}
                  className="h-9"
                  onKeyDown={async (e) => {
                    if (e.key === "Enter") {
                      const name = (e.target as HTMLInputElement).value.trim();
                      if (!name) return;
                      await onCreateMember({ name, color: nextColor });
                      setAdding(false);
                    }
                    if (e.key === "Escape") setAdding(false);
                  }}
                  onBlur={async (e) => {
                    const name = e.target.value.trim();
                    if (name) { await onCreateMember({ name, color: nextColor }); }
                    setAdding(false);
                  }}
                />
              </div>
            ) : (
              <Button variant="outline" className="w-full" onClick={() => setAdding(true)}>
                <Plus className="size-4" /> Добавить участника
              </Button>
            )}
          </section>

          <Separator />

          {/* Звук по умолчанию */}
          <section className="space-y-2.5">
            <h3 className="text-sm font-bold">Звук напоминаний</h3>
            <p className="text-xs text-muted-foreground">
              Мягкие сигналы вместо стандартного звука будильника. Для отдельной задачи можно выбрать свой звук
            </p>
            <div className="flex gap-2">
              <Select value={state.settings.sound} onValueChange={(v) => void onSaveSettings({ sound: v })}>
                <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {SOUND_OPTIONS.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      <span className="flex flex-col">
                        <span>{s.label}</span>
                        <span className="text-[10px] text-muted-foreground">{s.hint}</span>
                      </span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button variant="outline" size="icon" className="size-9 shrink-0" onClick={() => playSound(state.settings.sound)} aria-label="Прослушать выбранный звук">
                <Volume2 className="size-4" />
              </Button>
            </div>
          </section>

          <Separator />

          {/* Уведомления */}
          <section className="space-y-2.5">
            <h3 className="text-sm font-bold">Уведомления</h3>
            <div className="flex items-center justify-between gap-3">
              <label htmlFor="reminders-switch" className="text-sm">Напоминать о задачах</label>
              <Switch
                id="reminders-switch"
                checked={state.settings.reminders}
                onCheckedChange={(v) => void onSaveSettings({ reminders: v })}
              />
            </div>
            <p className="text-xs text-muted-foreground">
              Напоминание приходит, когда приложение или виджет открыты: звук + всплывающее уведомление
            </p>
            {permission === "default" && (
              <Button variant="outline" size="sm" onClick={requestPermission}>
                <BellRing className="size-4" /> Разрешить уведомления
              </Button>
            )}
            {permission === "granted" && (
              <p className="text-xs font-medium text-primary">Уведомления разрешены</p>
            )}
            {permission === "denied" && (
              <p className="text-xs text-destructive">Уведомления заблокированы в настройках браузера</p>
            )}
            {permission === "unsupported" && (
              <p className="text-xs text-muted-foreground">Браузер не поддерживает уведомления</p>
            )}
          </section>

          <Separator />

          {/* Тема */}
          <section className="space-y-2.5">
            <h3 className="text-sm font-bold">Оформление</h3>
            <div className="grid grid-cols-3 gap-1.5">
              {themeOptions.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setTheme(t.id)}
                  aria-pressed={theme === t.id}
                  className={cn(
                    "flex flex-col items-center gap-1 rounded-xl border py-2.5 text-[11px] font-semibold transition-colors",
                    theme === t.id ? "border-primary bg-primary/10 text-primary" : "text-muted-foreground hover:bg-accent",
                  )}
                >
                  <t.icon className="size-4" aria-hidden />
                  {t.label}
                </button>
              ))}
            </div>
          </section>

          <Separator />

          {/* Виджет */}
          <section className="space-y-2.5">
            <h3 className="flex items-center gap-2 text-sm font-bold">
              <TabletSmartphone className="size-4" aria-hidden /> Виджет на экране телефона
            </h3>
            <ol className="list-decimal space-y-1 pl-4 text-xs text-muted-foreground">
              <li>Нажмите «Открыть виджет»</li>
              <li>Android: меню ⋮ → «Добавить на главный экран»</li>
              <li>iPhone: кнопка «Поделиться» → «На экран „Домой“»</li>
            </ol>
            <Button asChild className="w-full">
              <a href="/?widget=1">Открыть виджет</a>
            </Button>
            <p className="text-xs text-muted-foreground">
              Виджет обновляется сам каждые 30 секунд и при каждом открытии — там всегда актуальная неделя
            </p>
          </section>
        </div>
      </SheetContent>
    </Sheet>
  );
}
