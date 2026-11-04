import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { ensureSeed } from "@/lib/seed";

export const dynamic = "force-dynamic";

export async function GET() {
  await ensureSeed();
  const [members, tasks, notes, settings] = await Promise.all([
    db.member.findMany({ orderBy: [{ order: "asc" }, { createdAt: "asc" }] }),
    db.task.findMany({ orderBy: [{ time: "asc" }, { createdAt: "asc" }] }),
    db.note.findMany({ orderBy: { updatedAt: "desc" } }),
    db.appSettings.findUnique({ where: { id: "main" } }),
  ]);

  return NextResponse.json(
    {
      members,
      tasks: tasks.map((t) => ({
        id: t.id,
        title: t.title,
        note: t.note,
        memberId: t.memberId,
        time: t.time,
        weekdays: t.weekdays,
        date: t.date,
        sound: t.sound,
        doneDates: t.doneDates ? t.doneDates.split(",").filter(Boolean) : [],
      })),
      notes,
      settings: settings ?? { sound: "chime", reminders: true },
      serverTime: new Date().toISOString(),
    },
    { headers: { "Cache-Control": "no-store" } },
  );
}
