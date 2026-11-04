import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ id: string }> };

// PATCH — редактирование, восстановление из корзины (trashed:false) или удаление в корзину (trashed:true)
export async function PATCH(req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const body = await req.json().catch(() => null);
  if (!body) return NextResponse.json({ error: "Некорректные данные" }, { status: 400 });

  const data: { title?: string; content?: string; trashed?: boolean } = {};
  if (typeof body.title === "string") {
    const title = body.title.trim();
    if (!title) return NextResponse.json({ error: "Заголовок не может быть пустым" }, { status: 400 });
    data.title = title;
  }
  if (typeof body.content === "string") data.content = body.content.slice(0, 20000);
  if (typeof body.trashed === "boolean") data.trashed = body.trashed;

  try {
    const note = await db.note.update({ where: { id }, data });
    return NextResponse.json(note);
  } catch {
    return NextResponse.json({ error: "Заметка не найдена" }, { status: 404 });
  }
}

// DELETE — безвозвратное удаление заметки
export async function DELETE(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  try {
    await db.note.delete({ where: { id } });
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: "Заметка не найдена" }, { status: 404 });
  }
}
