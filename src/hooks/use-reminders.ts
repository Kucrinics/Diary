"use client";

import { useEffect, useRef } from "react";
import { toast } from "sonner";
import type { AppState } from "@/lib/types";
import { currentWeek, dateKey, parseHM } from "@/lib/week";
import { playSound } from "@/lib/sounds";

const STORAGE_KEY = "fired-reminders";

type FiredMap = { day: string; keys: string[] };

function loadFired(today: string): Set<string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw) as FiredMap;
    return parsed.day === today ? new Set(parsed.keys) : new Set();
  } catch {
    return new Set();
  }
}

function saveFired(today: string, keys: Set<string>) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ day: today, keys: Array.from(keys) } satisfies FiredMap));
  } catch {
    /* ignore */
  }
}

// Напоминания: пока приложение (или виджет) открыто, проверяем задачи
// текущего дня и проигрываем выбранный звук + показываем уведомление.
export function useReminders(state: AppState | undefined) {
  const firedRef = useRef<Set<string>>(new Set());
  const stateRef = useRef<AppState | undefined>(undefined);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    const tick = () => {
      const s = stateRef.current;
      if (!s || !s.settings.reminders) return;

      const now = new Date();
      const today = dateKey(now);
      if (firedRef.current.size === 0) firedRef.current = loadFired(today);

      const week = currentWeek(s.tasks, s.members);
      const todayGroup = week.find((d) => d.key === today);
      if (!todayGroup) return;

      const nowMin = now.getHours() * 60 + now.getMinutes();

      for (const occ of todayGroup.items) {
        if (occ.done) continue;
        if (nowMin < occ.minutes || nowMin - occ.minutes > 2) continue;
        const key = `${occ.task.id}:${today}`;
        if (firedRef.current.has(key)) continue;
        firedRef.current.add(key);
        saveFired(today, firedRef.current);

        const sound = occ.task.sound ?? s.settings.sound;
        playSound(sound);

        const who = occ.member ? `${occ.member.name}: ` : "";
        const body = `В ${occ.task.time}${occ.task.note ? ` · ${occ.task.note}` : ""}`;
        try {
          if (typeof Notification !== "undefined" && Notification.permission === "granted") {
            const n = new Notification(`⏰ ${who}${occ.task.title}`, { body, tag: key });
            n.onclick = () => window.focus();
          }
        } catch {
          /* уведомления недоступны */
        }
        toast.info(`⏰ ${who}${occ.task.title}`, { description: body });
      }
    };

    tick();
    const iv = setInterval(tick, 15_000);
    return () => clearInterval(iv);
  }, []);
}
