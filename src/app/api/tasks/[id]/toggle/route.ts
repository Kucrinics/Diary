import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

type Ctx = { params: Promise<{ id: string }> };

// Отметить/снять выполнение задачи на конкретную дату
export async function POST(req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const body = await req.json().catch(() => null);
  const dateKey = typeof body?.dateKey === "string" && DATE_RE.test(body.dateKey) ? body.dateKey : null;
  if (!dateKey) return NextResponse.json({ error: "Некорректная дата" }, { status: 400 });

  const task = await db.task.findUnique({ where: { id } });
  if (!task) return NextResponse.json({ error: "Задача не найдена" }, { status: 404 });

  const set = new Set(task.doneDates ? task.doneDates.split(",").filter(Boolean) : []);
  if (set.has(dateKey)) set.delete(dateKey);
  else set.add(dateKey);

  const updated = await db.task.update({ where: { id }, data: { doneDates: Array.from(set).sort().join(",") } });
  return NextResponse.json({ ...updated, doneDates: Array.from(set).sort() });
}
