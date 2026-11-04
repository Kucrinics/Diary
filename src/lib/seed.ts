// Демонстрационные данные (создаются один раз при первом обращении к /api/state)

import { db } from "@/lib/db";

function keyOf(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export async function ensureSeed(): Promise<void> {
  const existing = await db.member.count();
  if (existing > 0) return;

  const [alisa, mark, family] = await Promise.all([
    db.member.create({ data: { name: "Алиса", color: "#ec4899", order: 0 } }),
    db.member.create({ data: { name: "Марк", color: "#14b8a6", order: 1 } }),
    db.member.create({ data: { name: "Вся семья", color: "#f97316", order: 2 } }),
  ]);

  const tasks: {
    title: string;
    note?: string;
    memberId: string;
    time: string;
    weekdays?: string;
    date?: string;
  }[] = [
    { title: "Школа", memberId: alisa.id, time: "08:00", weekdays: "1,2,3,4,5", note: "Не забыть сменку для физкультуры" },
    { title: "Бассейн", memberId: alisa.id, time: "16:30", weekdays: "2", note: "Шапочка и очки в спортивной сумке" },
    { title: "Английский", memberId: alisa.id, time: "18:30", weekdays: "4" },
    { title: "Хореография", memberId: alisa.id, time: "11:00", weekdays: "6" },
    { title: "День рождения Кати", memberId: alisa.id, time: "17:00", date: keyOf(2), note: "Купить открытку и подарить книгу" },
    { title: "Школа", memberId: mark.id, time: "08:15", weekdays: "1,2,3,4,5" },
    { title: "Футбол", memberId: mark.id, time: "18:00", weekdays: "1,3", note: "Форма после тренировки — в стирку" },
    { title: "Рисование", memberId: mark.id, time: "15:30", weekdays: "2" },
    { title: "Карате", memberId: mark.id, time: "17:00", weekdays: "5" },
    { title: "Стоматолог", memberId: mark.id, time: "09:30", date: keyOf(1) },
    { title: "Сборы в школу", memberId: family.id, time: "07:30", weekdays: "1,2,3,4,5" },
    { title: "Семейный ужин", memberId: family.id, time: "19:00", weekdays: "3", note: "Готовим пиццу вместе" },
    { title: "Планирование недели", memberId: family.id, time: "18:00", weekdays: "7" },
    { title: "Родительское собрание", memberId: family.id, time: "15:00", date: keyOf(1), note: "Кабинет 2 «А», взять тетрадь для заметок" },
    { title: "Обед у бабушки", memberId: family.id, time: "12:00", date: keyOf(4) },
  ];

  await db.task.createMany({
    data: tasks.map((t) => ({
      title: t.title,
      note: t.note ?? "",
      memberId: t.memberId,
      time: t.time,
      weekdays: t.weekdays ?? "",
      date: t.date ?? null,
    })),
  });

  await db.note.createMany({
    data: [
      {
        title: "Контакты",
        content:
          "Классная руководительница 2 «А» — Ирина Петровна\nТренер по плаванию — Сергей Игоревич\nСекция карате — запись в группе в четверг",
      },
      {
        title: "Купить к выходным",
        content: "Молоко, хлеб\nЯблоки и бананы\nНаклейки для дневника\nСвечи для торта",
      },
    ],
  });

  await db.appSettings.upsert({
    where: { id: "main" },
    update: {},
    create: { id: "main", sound: "chime", reminders: true },
  });
}
