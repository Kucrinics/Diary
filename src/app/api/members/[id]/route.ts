import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

const HEX = /^#[0-9a-fA-F]{6}$/;

type Ctx = { params: Promise<{ id: string }> };

export async function PATCH(req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const body = await req.json().catch(() => null);

  const data: { name?: string; color?: string; order?: number } = {};
  if (typeof body?.name === "string") {
    const name = body.name.trim();
    if (!name) return NextResponse.json({ error: "Имя не может быть пустым" }, { status: 400 });
    data.name = name;
  }
  if (typeof body?.color === "string" && HEX.test(body.color)) data.color = body.color;
  if (typeof body?.order === "number" && Number.isFinite(body.order)) data.order = body.order;

  try {
    const member = await db.member.update({ where: { id }, data });
    return NextResponse.json(member);
  } catch {
    return NextResponse.json({ error: "Участник не найден" }, { status: 404 });
  }
}

export async function DELETE(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  try {
    // Задачи остаются, но отвязываются от участника (onDelete: SetNull)
    await db.member.delete({ where: { id } });
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: "Участник не найден" }, { status: 404 });
  }
}
