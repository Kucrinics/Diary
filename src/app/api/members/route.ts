import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

const HEX = /^#[0-9a-fA-F]{6}$/;

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const name = typeof body?.name === "string" ? body.name.trim() : "";
  const color = typeof body?.color === "string" && HEX.test(body.color) ? body.color : "#14b8a6";
  if (!name) return NextResponse.json({ error: "Укажите имя участника" }, { status: 400 });

  const maxOrder = await db.member.aggregate({ _max: { order: true } });
  const member = await db.member.create({
    data: { name, color, order: (maxOrder._max.order ?? 0) + 1 },
  });
  return NextResponse.json(member, { status: 201 });
}
