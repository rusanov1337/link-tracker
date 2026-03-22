# LinkTracker

LinkTracker – Telegram-бот, который отслеживает изменения на веб-страницах и оперативно информирует пользователя о них.

Это шаблон проекта, который вам необходимо взять за основу для разработки своей системы.

## Telegram API (HW1)

### Требования

- JDK 25
- Maven Wrapper (`mvnw`)

### Подготовка токена

Получите токен у `@BotFather` и задайте его через переменную окружения `TELEGRAM_TOKEN`.

PowerShell (текущая сессия):

```powershell
$env:TELEGRAM_TOKEN="telegram_bot_token"
```

PowerShell (сохранить для пользователя Windows):

```powershell
setx TELEGRAM_TOKEN "telegram_bot_token"
```

После `setx` откройте новое окно терминала.

Bash (Linux/macOS, текущая сессия):

```bash
export TELEGRAM_TOKEN="telegram_bot_token"
```

### Локальная проверка бота

Сборка и тесты:

```powershell
.\mvnw.cmd clean verify
```

```bash
./mvnw clean verify
```

Запуск bot-модуля:

```powershell
java -jar .\bot\target\bot-0.0.1.jar --app.telegram.polling-enabled=true
```

```bash
java -jar ./bot/target/bot-0.0.1.jar --app.telegram.polling-enabled=true
```

Проверка в Telegram:

- `/start`
- `/help`
- любая неизвестная команда, например `/abc`

## PostgreSQL И Scrapper (HW3)

### Дополнительно требуется

- Docker
- `docker compose`

### Переменные окружения

Для локального запуска `scrapper`:

```bash
export SCRAPPER_DB_URL="jdbc:postgresql://localhost:5432/link_tracker"
export SCRAPPER_DB_USERNAME="postgres"
export SCRAPPER_DB_PASSWORD="postgres"
export SCRAPPER_DB_ACCESS_TYPE="SQL" # или ORM
export BOT_BASE_URL="http://localhost:8080"
```

При необходимости можно также задать:

```bash
export GITHUB_TOKEN=""
export STACKOVERFLOW_KEY=""
export STACKOVERFLOW_ACCESS_TOKEN=""
```

### Локальный запуск PostgreSQL

```bash
docker compose up -d
```

### Локальный запуск scrapper

```bash
java -jar ./scrapper/target/scrapper-0.0.1.jar
```

Миграции Liquibase применяются автоматически при старте `scrapper`.

### Переключение доступа к БД

- `SCRAPPER_DB_ACCESS_TYPE=SQL`
- `SCRAPPER_DB_ACCESS_TYPE=ORM`

Для интеграционных тестов нужен запущенный Docker.

Полезную для разработки проекта информацию вы можете найти в файле [HELP.md](./HELP.md).
