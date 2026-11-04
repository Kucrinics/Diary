import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import type { Prisma } from "@prisma/client";

export const dynamic = "force-dynamic";

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const WEEKDAYS_RE = /^[1-7](,[1-7])*$/;

// Проверка полей задачи; возвращает объект ошибки или null
function validate(body: Record<string, unknown> | null): string | null {
  if (!body) return "Некорректные данные";
  const title = typeof body.title === "string" ? body.title.trim() : "";
  if (!title) return "Введите название задачи";
  if (typeof body.time !== "string" || !TIME_RE.test(body.time)) return "Укажите время в формате ЧЧ:ММ";
  const weekly = typeof body.weekdays === "string" && body.weekdays.length > 0;
  const once = typeof body.date === "string" && body.date.length > 0;
  if (!weekly && !once) return "Выберите дни недели или дату";
  if (weekly && !WEEKDAYS_RE.test(body.weekdays as string)) return "Некорректные дни недели";
  if (once && !DATE_RE.test(body.date as string)) return "Некорректная дата";
  return null;
}

function pickFields(body: Record<string, unknown>): Prisma.TaskUncheckedCreateInput {
  const memberId = typeof body.memberId === "string" && body.memberId.length > 0 ? body.memberId : null;
  const sound = typeof body.sound === "string" && body.sound.length > 0 ? body.sound : null;
  return {
    title: (body.title as string).trim(),
    note: typeof body.note === "string" ? body.note.slice(0, 2000) : "",
    memberId,
    time: body.time as string,
    weekdays: typeof body.weekdays === "string" ? body.weekdays : "",
    date: typeof body.date === "string" && body.date.length > 0 ? body.date : null,
    sound,
  };
}

export async function POST(req: NextRequest) {
  const body = (await req.json().catch(() => null)) as Record<string, unknown> | null;
  const err = validate(body);
  if (err || !body) return NextResponse.json({ error: err ?? "Некорректные данные" }, { status: 400 });

  if (typeof body.memberId === "string" && body.memberId.length > 0) {
    const exists = await db.member.findUnique({ where: { id: body.memberId } });
    if (!exists) return NextResponse.json({ error: "Участник не найден" }, { status: 400 });
  }

  const task = await db.task.create({ data: pickFields(body) });
  return NextResponse.json(task, { status: 201 });
}
