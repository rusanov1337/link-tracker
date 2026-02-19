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

Полезную для разработки проекта информацию вы можете найти в файле [HELP.md](./HELP.md).
