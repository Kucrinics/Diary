# Ежедневник — Android-приложение для RuStore

Полностью структурированный каркас Android-приложения «Ежедневник» на **Kotlin + Jetpack Compose + Room**, готовый к открытию в Android Studio и публикации в RuStore.

---

## Стек технологий
- **Kotlin** 2.0
- **Jetpack Compose** (Material 3) — UI
- **Room** — локальная база данных (данные хранятся на устройстве)
- **Navigation Compose** — навигация между экранами
- **AlarmManager + Notifications** — локальные напоминания
- **MVVM + Repository** — архитектура
- **Ручной DI** (без Hilt) — для простоты

---

## Структура проекта
```
Ежедневник/
├── settings.gradle.kts          # подключение модулей, репозитории
├── build.gradle.kts             # корневые плагины
├── gradle.properties            # настройки Gradle
├── gradle/
│   ├── libs.versions.toml       # каталог зависимостей и версий
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts         # конфигурация модуля app + зависимости
    ├── proguard-rules.pro       # правила обфускации для релиза
    └── src/main/
        ├── AndroidManifest.xml  # разрешения, Activity, Receiver
        ├── res/                 # строки, цвета, темы, иконки
        └── java/ru/diary/app/
            ├── DiaryApp.kt          # класс Application
            ├── MainActivity.kt      # точка входа
            ├── di/AppContainer.kt   # контейнер зависимостей
            ├── data/
            │   ├── model/           # Entity: Task, Category, Habit, HabitEntry, Note
            │   ├── db/              # AppDatabase, DAO, DateUtils
            │   └── repository/      # TaskRepository, HabitRepository, ...
            ├── notifications/       # AlarmScheduler, Receivers, NotificationHelper
            └── ui/
                ├── theme/           # Color/Type/Theme (Material 3)
                ├── BottomTab.kt     # модель вкладок
                ├── DiaryApp.kt      # NavHost + нижний бар
                ├── tasks/           # экран задач + ViewModel + диалоги
                ├── calendar/        # экран календаря
                ├── habits/          # экран привычек
                └── notes/           # экран заметок
```

## Реализованный функционал (MVP)
- ✅ Задачи на день: создание, редактирование, удаление, отметка выполнения
- ✅ Перенос задачи на выбранную дату
- ✅ Календарь месяца с точками под днями (где есть задачи)
- ✅ Привычки: отметка за сегодня, подсчёт серии (streak)
- ✅ Заметки (заголовок + текст)
- ✅ Локальные напоминания через AlarmManager + Notifications
- ✅ Тёмная и светлая темы

---

## Как запустить

### 1. Установите Android Studio
Скачать: <https://developer.android.com/studio>
Версия: Hedgehog (2023.1.1) или новее.

### 2. Откройте проект
**File → Open → выберите папку `Ежедневник`** (с файлом `settings.gradle.kts`).
Android Studio предложит скачать Gradle и SDK — согласитесь.

### 3. Проверьте JDK
В проекте используется Java 17. Android Studio последних версий включает его по умолчанию.
Если ругается: **File → Settings → Build → Build Tools → Gradle → Gradle JDK → выберите 17**.

### 4. Создайте эмулятор
**Tools → Device Manager → Create Device** → выберите Pixel 7 → скачайте образ API 34.

### 5. Запустите
Нажмите зелёную кнопку **Run** (Shift+F10). Приложение должно открыться на эмуляторе.

---

## Как собрать релизный APK для RuStore

### 1. Создайте keystore (ОДНОРАЗОВО!)
В Android Studio: **Build → Generate Signed Bundle / APK → APK → Create new...**
- Key store path: выберите путь ВНЕ проекта (например, `C:\keystores\diary.jks`)
- Придумайте пароли (сохраните их в надёжном месте!)
- Alias: `diary`
- Validity: 25 лет
- Заполните хотя бы одно поле (CN — ваше имя)

> ⚠️ **Keystore — это ваша подпись.** Если потеряете — не сможете выпускать обновления. Резервная копия обязательна.

### 2. Создайте keystore.properties (рядом с settings.gradle.kts)
```properties
storeFile=C:/keystores/diary.jks
storePassword=ВАШ_ПАРОЛЬ
keyAlias=diary
keyPassword=ВАШ_ПАРОЛЬ
```
Файл уже в `.gitignore` — не попадёт в репозиторий.

### 3. Соберите APK
**Build → Generate Signed Bundle / APK → APK → release → Finish.**

Готовый файл: `app/build/outputs/apk/release/app-release.apk`.
### 4. Отпечаток релизного ключа
Сертификат подписи, SHA-256:
```
e21cc5a330164264db51b78a5c99cf990221c849d40c3b47182fe71f3ad002ac
```
Этим ключом подписаны версии в RuStore (начиная с 1.7.8). Файл ключа:
`diary-release.jks` (alias `diary-key`) — хранится вне репозитория, бэкап обязателен.


---

## Публикация в RuStore

1. Зарегистрируйтесь как разработчик: <https://www.rustore.ru/help/developers/publish-app>
   - Нужен статус **ИП, ООО или самозанятого** (физлицам публиковать нельзя).
   - Самозанятость оформляется за 5 минут через приложение «Мой налог».

2. Подготовьте материалы:
   - Иконка 512×512 PNG
   - Скриншоты (минимум 2, лучше 5–7)
   - Краткое и полное описание (русский)
   - **Политика конфиденциальности** (обязательно!) — текстовая страница с описанием:
     - какие данные собирает приложение (в MVP — никакие, всё локально),
     - как использует уведомления,
     - контакты разработчика.
     Разместите, например, на GitHub Pages.

3. Создайте приложение в консоли RuStore, заполните анкету, загрузите APK.

4. Отправьте на модерацию (обычно 1–3 дня).

---

## Дополнительные шаги (после MVP)
- Привязка задач к категориям (модели уже есть, нужен UI выбора категории).
- Вид месяца/недели в календаре.
- Виджеты на рабочий стол.
- Резервное копирование и синхронизация.
- Поиск по задачам и заметкам.

---

## О монетизации (реклама AdEx)
В MVP реклама **не подключена**. Если захотите добавить Ambire AdEx:
- Технически возможно (SDK/JS-теги для Android).
- ⚠️ Для **RuStore** предпочтительнее российские сети (Яндекс РСЯ, VK Ads) —
  из-за требований 149-ФЗ и закона «О рекламе» (маркировка рекламы через ЕРИР).
  AdEx не имеет российской юрисдикции и может вызвать вопросы при модерации.
- Рекомендация: сначала выпустите приложение без монетизации, затем добавляйте рекламу отдельным обновлением.
