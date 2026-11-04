import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import { SOUND_OPTIONS } from "@/lib/sounds";

export const dynamic = "force-dynamic";

export async function PUT(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const data: { sound?: string; reminders?: boolean } = {};

  if (typeof body?.sound === "string" && SOUND_OPTIONS.some((s) => s.id === body.sound)) {
    data.sound = body.sound;
  }
  if (typeof body?.reminders === "boolean") data.reminders = body.reminders;

  const settings = await db.appSettings.upsert({
    where: { id: "main" },
    update: data,
    create: { id: "main", sound: data.sound ?? "chime", reminders: data.reminders ?? true },
  });
  return NextResponse.json(settings);
}
