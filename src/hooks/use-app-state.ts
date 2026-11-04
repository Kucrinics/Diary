"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { AppState, TaskPayload } from "@/lib/types";

export async function fetchState(): Promise<AppState> {
  const res = await fetch("/api/state", { cache: "no-store" });
  if (!res.ok) throw new Error("Не удалось загрузить данные");
  return res.json();
}

export function useAppState(refetchInterval = 60_000) {
  return useQuery({
    queryKey: ["state"],
    queryFn: fetchState,
    refetchInterval,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    staleTime: 5_000,
  });
}

async function req(url: string, method: string, body?: unknown) {
  const res = await fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = (await res.json().catch(() => ({}))) as { error?: string };
  if (!res.ok) throw new Error(data.error ?? "Не удалось сохранить. Попробуйте ещё раз");
  return data;
}

// Все мутации подтверждаются сервером и только затем обновляют интерфейс —
// правка не может «потеряться» (отзыв: «некоторые задачи не сохраняют редактирование»)
export function useMutations() {
  const qc = useQueryClient();
  const refresh = () => qc.invalidateQueries({ queryKey: ["state"] });

  return {
    createTask: async (p: TaskPayload) => {
      await req("/api/tasks", "POST", p);
      await refresh();
    },
    updateTask: async (id: string, p: Partial<TaskPayload>) => {
      await req(`/api/tasks/${id}`, "PATCH", p);
      await refresh();
    },
    deleteTask: async (id: string) => {
      await req(`/api/tasks/${id}`, "DELETE");
      await refresh();
    },
    toggleTask: async (id: string, dateKey: string) => {
      await req(`/api/tasks/${id}/toggle`, "POST", { dateKey });
      await refresh();
    },
    createNote: async (p: { title: string; content: string }) => {
      await req("/api/notes", "POST", p);
      await refresh();
    },
    updateNote: async (id: string, p: { title?: string; content?: string; trashed?: boolean }) => {
      await req(`/api/notes/${id}`, "PATCH", p);
      await refresh();
    },
    deleteNoteForever: async (id: string) => {
      await req(`/api/notes/${id}`, "DELETE");
      await refresh();
    },
    emptyTrash: async () => {
      await req("/api/notes?scope=trash", "DELETE");
      await refresh();
    },
    createMember: async (p: { name: string; color: string }) => {
      await req("/api/members", "POST", p);
      await refresh();
    },
    updateMember: async (id: string, p: { name?: string; color?: string }) => {
      await req(`/api/members/${id}`, "PATCH", p);
      await refresh();
    },
    deleteMember: async (id: string) => {
      await req(`/api/members/${id}`, "DELETE");
      await refresh();
    },
    saveSettings: async (p: { sound?: string; reminders?: boolean }) => {
      await req("/api/settings", "PUT", p);
      await refresh();
    },
  };
}
