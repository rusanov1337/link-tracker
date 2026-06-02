# Observability

## Что подключено

В проект добавлены Prometheus-метрики через Spring Boot Actuator и Micrometer Prometheus Registry.

Endpoints:

- Bot: `http://localhost:8011/metrics`
- Scrapper: `http://localhost:8081/metrics`
- AI Agent: `http://localhost:8082/metrics`

Инфраструктура мониторинга:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Grafana login/password: `admin` / `admin`

Файлы конфигурации:

- `observability/prometheus/prometheus.yml`
- `observability/grafana/provisioning/datasources/prometheus.yml`
- `observability/grafana/provisioning/dashboards/dashboards.yml`
- `observability/grafana/dashboards/link-tracker-red.json`
- `observability/grafana/dashboards/link-tracker-business.json`
- `example_pql.txt`

## Запуск

Сначала соберите сервисы:

```bash
./mvnw clean package -DskipTests
```

Поднимите инфраструктуру:

```bash
docker compose up -d postgres kafka-1 kafka-2 kafka-3 kafka-init schema-registry valkey-1 valkey-2 valkey-3 valkey-init prometheus grafana
```

Запустите приложения локально:

```bash
java -jar ./bot/target/bot-0.0.1.jar --app.telegram.polling-enabled=true
```

```bash
java -jar ./scrapper/target/scrapper-0.0.1.jar
```

```bash
java -jar ./ai-agent/target/ai-agent-0.0.1.jar
```

Prometheus запускается в Docker и обращается к приложениям на хосте через `host.docker.internal`. Для Linux в `docker-compose.yml` добавлен `host-gateway`.

## Проверка targets

Откройте:

```text
http://localhost:9090/targets
```

Ожидаемые targets:

- `bot-management`
- `scrapper-http`
- `ai-agent-http`

Если target находится в состоянии `DOWN`, проверьте, что соответствующий сервис запущен и слушает порт из раздела endpoints.

## Метрики Scrapper

`links_on_track`

- Type: Gauge
- Labels: `tracked_source`
- Description: количество ссылок, поставленных на мониторинг, с разбивкой по источнику `github` / `stackoverflow`.

`request_duration_ms_total`

- Type: Timer/Histogram
- Labels: `scope`, `scope_type`
- Description: длительность операций Scrapper.
- Scopes:
  - `database`, `links`
  - `external_source`, `github`
  - `external_source`, `stackoverflow`
  - `bot_transport`, `link_update`
  - `bot_transport`, `processing_failure_report`

В Prometheus histogram отображается как `request_duration_ms_total_seconds_bucket`, `request_duration_ms_total_seconds_count`, `request_duration_ms_total_seconds_sum`.

`api_requests_total`

- Type: Counter
- Labels: `source`
- Description: количество запросов к Scrapper API.
- Sources:
  - `http`
  - `grpc`

## Метрики Bot

`command_requests_total`

- Type: Counter
- Labels: `command`, `status`
- Description: количество обработанных команд Bot.

`command_duration_ms_total`

- Type: Timer/Histogram
- Labels: `scope`, `scope_type`
- Description: длительность обработки команд и вызовов Scrapper API из Bot.
- Scopes:
  - `command`, `<command>`
  - `scrapper_sync_api`, `registerChat`
  - `scrapper_sync_api`, `getLinks`
  - `scrapper_sync_api`, `addLink`
  - `scrapper_sync_api`, `removeLink`

В Prometheus histogram отображается как `command_duration_ms_total_seconds_bucket`, `command_duration_ms_total_seconds_count`, `command_duration_ms_total_seconds_sum`.

`sent_notification_total`

- Type: Counter
- Description: количество успешно отправленных уведомлений пользователям.

`telegram_requests_total`

- Type: Counter
- Labels: `request_type`
- Description: количество входящих запросов/сообщений из Telegram polling.

## RED-метрики

Стандартные HTTP RED-метрики берутся из Spring Boot Actuator:

- `http_server_requests_seconds_count`
- `http_server_requests_seconds_sum`
- `http_server_requests_seconds_bucket`

В Grafana dashboard `Link Tracker RED Overview` добавлены:

- request rate;
- 5xx error rate;
- P95 HTTP latency;
- JVM memory usage;
- active tracked links.

Dashboard параметризован переменной `application`, которая строится по label из Prometheus target.

В Grafana dashboard `Link Tracker Business Metrics` добавлены:

- количество пользовательских Telegram-сообщений в секунду;
- количество активных ссылок по домену;
- количество отправленных уведомлений в секунду;
- p50/p95/p99 длительности scrape-операций по источнику;
- p50/p95/p99 длительности обработки команд Bot;
- количество обработанных команд Bot по команде и статусу;
- количество запросов к Scrapper API по источнику;
- p50/p95/p99 длительности вызовов Scrapper API из Bot.

Business dashboard параметризован переменной `app_type` со значениями `bot` / `scrapper`.

## PromQL

Основные запросы лежат в `example_pql.txt`.

Для панелей с latency используются histogram buckets и `histogram_quantile`.

Пример:

```promql
histogram_quantile(
  0.95,
  sum by (le, scope_type) (
    rate(request_duration_ms_total_seconds_bucket{application="scrapper", scope="external_source"}[5m])
  )
)
```

## Как получить данные на графиках

Prometheus начинает собирать значения только после запуска. Исторических данных до запуска Prometheus не будет.

Чтобы графики не были пустыми, выполните несколько API-запросов:

```bash
curl -X POST http://localhost:8081/tg-chat/1001
curl -X POST http://localhost:8081/links \
  -H 'Tg-Chat-Id: 1001' \
  -H 'Content-Type: application/json' \
  -d '{"link":"https://github.com/octocat/Hello-World","tags":[],"filters":[]}'
curl -H 'Tg-Chat-Id: 1001' http://localhost:8081/links
```

Для Bot-метрик отправьте команды в Telegram:

- `/start`
- `/help`
- `/list`

Для метрик scrape duration дождитесь запуска scheduler в Scrapper или вызовите сценарий, который приводит к проверке отслеживаемых ссылок.
