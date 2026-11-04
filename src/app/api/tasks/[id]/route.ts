import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import type { Prisma } from "@prisma/client";

export const dynamic = "force-dynamic";

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const WEEKDAYS_RE = /^[1-7](,[1-7])*$/;

type Ctx = { params: Promise<{ id: string }> };

export async function PATCH(req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const body = (await req.json().catch(() => null)) as Record<string, unknown> | null;
  if (!body) return NextResponse.json({ error: "Некорректные данные" }, { status: 400 });

  // Проверяем итоговое состояние (частичное обновление + существующие поля)
  const current = await db.task.findUnique({ where: { id } });
  if (!current) return NextResponse.json({ error: "Задача не найдена" }, { status: 404 });

  const title = typeof body.title === "string" ? body.title.trim() : current.title;
  const time = typeof body.time === "string" ? body.time : current.time;
  const weekdays = typeof body.weekdays === "string" ? body.weekdays : current.weekdays;
  const date =
    body.date === null ? null : typeof body.date === "string" && body.date.length > 0 ? body.date : current.date;

  if (!title) return NextResponse.json({ error: "Введите название задачи" }, { status: 400 });
  if (!TIME_RE.test(time)) return NextResponse.json({ error: "Укажите время в формате ЧЧ:ММ" }, { status: 400 });

  const weekly = weekdays.length > 0;
  const once = typeof date === "string" && date.length > 0;
  if (!weekly && !once) return NextResponse.json({ error: "Выберите дни недели или дату" }, { status: 400 });
  if (weekly && !WEEKDAYS_RE.test(weekdays)) return NextResponse.json({ error: "Некорректные дни недели" }, { status: 400 });
  if (once && date && !DATE_RE.test(date)) return NextResponse.json({ error: "Некорректная дата" }, { status: 400 });

  const data: Prisma.TaskUncheckedUpdateInput = { title, time, weekdays, date };
  if (typeof body.note === "string") data.note = body.note.slice(0, 2000);
  if (body.memberId === null || typeof body.memberId === "string")
    data.memberId = body.memberId === null ? null : body.memberId;
  if (body.sound === null || typeof body.sound === "string")
    data.sound = body.sound === null || body.sound === "" ? null : body.sound;

  try {
    const task = await db.task.update({ where: { id }, data });
    return NextResponse.json(task);
  } catch {
    return NextResponse.json({ error: "Не удалось сохранить задачу" }, { status: 500 });
  }
}

export async function DELETE(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  try {
    await db.task.delete({ where: { id } });
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: "Задача не найдена" }, { status: 404 });
  }
}
