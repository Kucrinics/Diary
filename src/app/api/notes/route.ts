import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const title = typeof body?.title === "string" ? body.title.trim() : "";
  if (!title) return NextResponse.json({ error: "Введите заголовок заметки" }, { status: 400 });
  const content = typeof body?.content === "string" ? body.content.slice(0, 20000) : "";
  const note = await db.note.create({ data: { title, content } });
  return NextResponse.json(note, { status: 201 });
}

// Очистка корзины: DELETE /api/notes?scope=trash
export async function DELETE(req: NextRequest) {
  const scope = req.nextUrl.searchParams.get("scope");
  if (scope === "trash") {
    const res = await db.note.deleteMany({ where: { trashed: true } });
    return NextResponse.json({ ok: true, deleted: res.count });
  }
  return NextResponse.json({ error: "Некорректный запрос" }, { status: 400 });
}
