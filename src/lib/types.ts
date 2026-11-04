// Общие типы приложения (совпадают с JSON из /api/state)

export type Member = {
  id: string;
  name: string;
  color: string;
  order: number;
};

export type Task = {
  id: string;
  title: string;
  note: string;
  memberId: string | null;
  time: string; // "16:30"
  weekdays: string; // "1,3,5" (1=ПН … 7=ВС) для еженедельных, "" для разовых
  date: string | null; // "2026-11-06" для разовых
  sound: string | null; // null = как в настройках
  doneDates: string[]; // ключи выполненных дат ("2026-11-04")
};

export type Note = {
  id: string;
  title: string;
  content: string;
  trashed: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AppSettings = {
  sound: string;
  reminders: boolean;
};

export type AppState = {
  members: Member[];
  tasks: Task[];
  notes: Note[];
  settings: AppSettings;
  serverTime: string;
};

export type TaskPayload = {
  title: string;
  note: string;
  memberId: string | null;
  time: string;
  weekdays: string;
  date: string | null;
  sound: string | null;
};
