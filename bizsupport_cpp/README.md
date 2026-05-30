# BizSupport (C++)

C++17 порт Spring Boot приложения BizSupport — информационной системы поддержки малого и среднего бизнеса в России: налоговые режимы, госзакупки (44-ФЗ / 223-ФЗ), тендеры, чек-листы, дедлайны, ИИ-ассистент.

## Стек

| Слой | Реализация |
|---|---|
| HTTP-сервер | [cpp-httplib](https://github.com/yhirose/cpp-httplib) (вендорится в `third_party/`) |
| JSON | [nlohmann/json](https://github.com/nlohmann/json) (вендорится в `third_party/`) |
| База данных | PostgreSQL 16 + [libpqxx](https://github.com/jtv/libpqxx) |
| Аутентификация | JWT (HMAC-SHA256) + PBKDF2-SHA256 для паролей, OpenSSL |
| Фронтенд | Vanilla JS (MPA), один `common.js` хелпер, общий `style.css` |
| Сборка | CMake 3.14+, опционально Ninja и ccache |
| Контейнеризация | Multi-stage Dockerfile + docker-compose |

## Быстрый запуск (Docker)

```bash
cd bizsupport_cpp
docker compose up --build
```

Открыть [http://localhost:8080](http://localhost:8080).
Демо-вход: `admin@bizsupport.ru` / `admin123`.

PostgreSQL экспонируется на хосте через порт `5433` (внутри сети — `5432`).
SQL-схема и seed-данные применяются автоматически из `sql/init.sql` при первом старте.

## Локальный запуск (без Docker)

Требования: `gcc/clang` с C++17, `cmake` 3.14+, `pkg-config`, `libpqxx-dev`, `libssl-dev`, `libpq-dev`, PostgreSQL 13+.

```bash
# 1. База данных
sudo -u postgres psql <<SQL
CREATE USER bizsupport_user WITH PASSWORD 'bizsupport_pass';
CREATE DATABASE bizsupport_db OWNER bizsupport_user;
SQL
PGPASSWORD=bizsupport_pass psql -h localhost -U bizsupport_user -d bizsupport_db -f sql/init.sql

# 2. Сборка
cmake -B build -G Ninja -DCMAKE_BUILD_TYPE=Release   # без -G Ninja тоже работает
cmake --build build -j

# 3. Запуск
./build/bizsupport config.ini
```

Открыть [http://localhost:8080](http://localhost:8080).

## Конфигурация

Параметры читаются из `config.ini`, а переменные окружения их переопределяют:

| ENV | INI-ключ | По умолчанию | Описание |
|---|---|---|---|
| `DB_HOST` | `database.host` | `localhost` | Хост PostgreSQL |
| `DB_PORT` | `database.port` | `5432` | Порт |
| `DB_NAME` | `database.dbname` | `bizsupport_db` | Имя БД |
| `DB_USER` | `database.user` | `bizsupport_user` | Пользователь |
| `DB_PASSWORD` | `database.password` | `bizsupport_pass` | Пароль |
| `APP_JWT_SECRET` | `jwt.secret` | dev-значение | Секрет для подписи JWT (поменять в проде!) |
| `WEB_ROOT` | — | `./web` | Каталог с UI-страницами |

ИИ-ассистент включается, если в `[assistant]` секции `config.ini` указан реальный API-ключ совместимого с OpenAI провайдера (DeepSeek, OpenRouter, локальный Ollama/LM Studio и т.п.). Иначе вкладка «Ассистент» покажет «не настроен».

## REST API

База — `/api`. Все защищённые роуты ожидают `Authorization: Bearer <token>` или cookie `jwt=<token>`.

| Раздел | Эндпоинты |
|---|---|
| Auth | `POST /auth/{register,login}` |
| Dashboard | `GET /dashboard` |
| Profile | `GET/POST /profile`, `GET /profile/me` |
| Tax | `GET /tax/{regimes,regimes/{code},recommend,current,deadlines,calculator}`, `POST /tax/set-regime` |
| Procurement | `GET /procurement/{scenarios,scenarios/{id},checklists,checklists/{id},templates}`, `POST /procurement/checklists/copy/{templateId}`, `POST /procurement/checklists/step/{stepId}/toggle` |
| Tenders | `GET /tenders`, `GET /tenders/{id}`, `GET /tenders/stats` |
| Notifications | `GET /notifications{,/unread,/count}`, `POST /notifications/{id}/read`, `POST /notifications/read-all`, `DELETE /notifications/{id}` |
| Favorites | `GET /favorites{,/type/{type}}`, `GET /favorites/check`, `POST /favorites/toggle` |
| Legal | `GET /legal`, `GET /legal/type/{type}` |
| Search | `GET /search?q=...` |
| Assistant | `POST /assistant/chat`, `GET/DELETE /assistant/history`, `GET /assistant/status` |
| Admin (ADMIN) | `GET /admin/{stats,users,regimes,scenarios,risks}` + `POST` save/delete |

## Структура проекта

```
bizsupport_cpp/
├── CMakeLists.txt
├── Dockerfile, docker-compose.yml
├── config.ini                — конфиг приложения
├── sql/init.sql              — схема + seed-данные (5 налоговых режимов, 3 сценария, 50 тендеров, ...)
├── src/
│   ├── main.cpp              — точка входа, все REST-маршруты
│   ├── models.hpp            — все сущности + enum'ы
│   ├── database.hpp          — пул подключений к PostgreSQL
│   ├── repositories.hpp      — слой доступа к данным (64 метода)
│   ├── jwt_utils.hpp         — JWT (HMAC-SHA256)
│   ├── password_utils.hpp    — PBKDF2-SHA256
│   ├── json_utils.hpp        — сериализация моделей
│   ├── auth_service.hpp      — авторизация
│   ├── tax_service.hpp       — налоговые режимы + рекомендатор
│   ├── tax_calculator_service.hpp — расчёт нагрузки по 5 режимам
│   ├── tender_service.hpp    — поиск тендеров с фильтрами
│   ├── procurement_service.hpp    — сценарии и чек-листы
│   ├── notification_service.hpp   — напоминания (фоновый поток)
│   ├── favorite_service.hpp, legal_reference_service.hpp
│   ├── assistant_service.hpp — клиент OpenAI-совместимого API
│   ├── company_profile_service.hpp
│   └── mock_tender_provider.hpp   — 50 моковых тендеров
├── third_party/              — вендоренные httplib.h и nlohmann/json.hpp
└── web/                      — фронтенд (HTML/CSS/JS)
    ├── index.html            — вход / регистрация
    ├── dashboard.html, tax.html, calculator.html, tenders.html,
    │   procurement.html, assistant.html, notifications.html,
    │   favorites.html, profile.html, admin.html, search.html
    ├── css/style.css
    └── js/common.js          — Auth, api(), renderShell, хелперы
```

## Советы по разработке

- **Ninja + ccache** ускоряют пересборку. CMake сам подхватит их, если установлены:
  `sudo apt install ninja-build ccache`
- **Прекомпилированные заголовки** уже включены — первый билд греет PCH (~30s), потом инкрементальные сборки заметно быстрее.
- **Без интернета** — все зависимости вендорятся в `third_party/`, FetchContent не используется.
- **Логи**: сервер пишет в stdout. В Docker — `docker compose logs -f app`.

## Демо-данные после `init.sql`

- Админ: `admin@bizsupport.ru` / `admin123`
- 5 налоговых режимов: УСН 6%, УСН 15%, ОСНО, ПСН, НПД
- 3 сценария госзакупок (44-ФЗ общий, 44-ФЗ МСП, 223-ФЗ МСП)
- 1 шаблонный чек-лист с 9 шагами
- 10 карточек рисков
- 16 правовых ссылок
- 50 мок-тендеров (генерируются в БД при первом старте, если таблица пуста)
