# Начало работы

## Структура проекта

Это многомодульный Java-проект, который собирается с помощью инструмента автоматической
сборки проектов [Apache Maven](https://maven.apache.org/).

Проект состоит из следующих модулей:

- [bot/](./bot) – сервис Telegram-бота, отвечающий за взаимодействие с пользователем
  через [Telegram Bot API](https://core.telegram.org/bots/api)
- [scrapper/](./scrapper) – сервис мониторинга контента, отвечающий за отслеживание
  изменений по ссылкам и отправку обновлений
- [ai-agent/](./ai-agent) – сервис интеллектуальной обработки данных
- [build-report-aggregate/](./build-report-aggregate) – служебный модуль для
  агрегирования отчётов сборки

Каждый модуль имеет стандартную структуру:

- `pom.xml` – дескриптор сборки модуля
- `src/main` – исходный код приложения
- `src/test` – тесты приложения

Общие файлы проекта:

- [pom.xml](./pom.xml) – корневой дескриптор сборки (parent POM), в котором описаны
  общие зависимости, плагины и настройки для всех модулей
- [mvnw](./mvnw) и [mvnw.cmd](./mvnw.cmd) – скрипты maven wrapper для Unix и
  Windows, которые позволяют запускать команды maven без локальной установки
- [pmd.xml](pmd.xml) и [spotbugs-excludes.xml](spotbugs-excludes.xml) – в проекте
  используются [линтеры](https://en.wikipedia.org/wiki/Lint_%28software%29) для контроля
  качества кода. Указанные файлы содержат правила для используемых линтеров
- [.mvn/](./.mvn) – служебная директория maven, содержащая конфигурационные
  параметры сборщика
- [lombok.config](lombok.config) – конфигурационный файл
  [Lombok](https://projectlombok.org/), библиотеки помогающей избежать рутинного
  написания шаблонного кода
- [.editorconfig](.editorconfig) – файл с описанием настроек форматирования кода
- [.gitlab-ci.yml](.gitlab-ci.yml) – файл с описанием шагов сборки проекта
  в среде GitLab
- [.gitattributes](.gitattributes), [.gitignore](.gitignore) – служебные файлы
  для git, с описанием того, как обрабатывать различные файлы, и какие из них
  игнорировать

## Начало работы

Для того чтобы собрать проект, и проверить, что все работает корректно, можно
запустить из модального окна IDEA
[Run Anything](https://www.jetbrains.com/help/idea/running-anything.html)
команду:

```shell
mvn clean verify
```

Альтернативно можно в терминале из корня проекта выполнить следующие команды.

Для Unix (Linux, macOS, Cygwin, WSL):

```shell
./mvnw clean verify
```

Для Windows:

```shell
mvnw.cmd clean verify
```

Для окончания сборки потребуется подождать какое-то время, пока maven скачает
все необходимые зависимости, скомпилирует проект и прогонит базовый набор
тестов.

Если вы в процессе сборки получили ошибку:

```shell
Rule 0: org.apache.maven.enforcer.rules.version.RequireJavaVersion failed with message:
JDK version must be at least 25
```

Значит, версия вашего JDK ниже 25.

Если же получили ошибку:

```shell
Rule 1: org.apache.maven.enforcer.rules.version.RequireMavenVersion failed with message:
Maven version should, at least, be 3.9.12
```

Значит, у вас используется версия maven ниже 3.9.12. Такого не должно произойти,
если вы запускаете сборку из IDEA или через `mvnw`-скрипты.

Далее будут перечислены другие полезные команды maven.

Для автоматического форматирования кода используйте команду:

```shell
mvn spotless:apply
```

Запуск только компиляции основных классов:

```shell
mvn compile
```

Запуск тестов:

```shell
mvn test
```

Запуск линтеров:

```shell
mvn clean compile -am spotless:check modernizer:modernizer spotbugs:check pmd:check pmd:cpd-check
```

Вывод дерева зависимостей проекта (полезно при отладке транзитивных
зависимостей):

```shell
mvn dependency:tree
```

Вывод вспомогательной информации о любом плагине (вместо `compiler` можно
подставить интересующий вас плагин):

```shell
mvn help:describe -Dplugin=compiler
```

## Конфигурация

Токены и другие секреты не должны храниться в исходном коде. Для хранения секретов
используйте `.env`-файлы в корне каждого модуля.

Пример `.env`-файла для модуля `bot`:

```properties
APP_TELEGRAM_TOKEN=123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11
```

Spring Boot автоматически подхватывает переменные окружения. Переменная
`APP_TELEGRAM_TOKEN` соответствует свойству `app.telegram.token` в `application.yaml`.

Убедитесь, что `.env`-файлы добавлены в `.gitignore` и не попадают в репозиторий.

## Зависимости

Подключение сторонних зависимостей, не указанных в шаблоне, запрещено без
предварительного согласования в чате курса. Допускается использование:

- Зависимостей, перечисленных в корневом [pom.xml](./pom.xml). Также, в pom.xml каждого модуля
  закомментированы некоторые зависимости — при необходимости их можно раскомментировать
- Любых зависимостей из экосистемы Spring (Spring Boot стартеры, Spring Cloud и т.д.)

## Внешние API

В проекте используются следующие внешние API:

* [Telegram Bot API](https://core.telegram.org/bots/api) — API для взаимодействия
  с Telegram. В проекте используется SDK
  [java-telegram-bot-api](https://github.com/pengrad/java-telegram-bot-api)
* [GitHub REST API](https://docs.github.com/en/rest) — API для получения информации
  о репозиториях, коммитах, issues и pull requests
* [StackOverflow API](https://api.stackexchange.com/docs) — API для получения
  информации о вопросах и ответах

Контракт взаимодействия между сервисами Bot и Scrapper описан в
[OpenAPI-спецификации](https://gist.github.com/sanyarnd/e35dc3d4e0c8000205ec5029dac38f5a).

### Справочная документация по Spring

Для дополнительной информации обратитесь к следующим разделам:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.2/maven-plugin)
* [Spring Configuration Processor](https://docs.spring.io/spring-boot/4.0.2/specification/configuration-metadata/annotation-processor.html)
* [Validation](https://docs.spring.io/spring-boot/4.0.2/reference/io/validation.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.2/reference/web/servlet.html)
* [HTTP Client](https://docs.spring.io/spring-boot/4.0.2/reference/io/rest-client.html#io.rest-client.restclient)
* [Spring Data JDBC](https://docs.spring.io/spring-boot/4.0.2/reference/data/sql.html#data.sql.jdbc)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.2/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Flyway Migration](https://docs.spring.io/spring-boot/4.0.2/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
* [Liquibase Migration](https://docs.spring.io/spring-boot/4.0.2/how-to/data-initialization.html#howto.data-initialization.migration-tool.liquibase)
* [Spring Data Redis (Access+Driver)](https://docs.spring.io/spring-boot/4.0.2/reference/data/nosql.html#data.nosql.redis)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.0.2/reference/actuator/index.html)
* [Task Execution and Scheduling](https://docs.spring.io/spring-boot/4.0.2/reference/features/task-execution-and-scheduling.html)
* [Spring for Apache Kafka](https://docs.spring.io/spring-boot/4.0.2/reference/messaging/kafka.html)
* [Spring for GraphQL](https://docs.spring.io/spring-boot/4.0.2/reference/web/spring-graphql.html)
* [Spring gRPC](https://docs.spring.io/spring-grpc/reference/index.html)
* [Resilience4J](https://docs.spring.io/spring-cloud-circuitbreaker/reference/spring-cloud-circuitbreaker-resilience4j.html)
* [Spring AI Models](https://docs.spring.io/spring-ai/reference/api/index.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.0.2/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.2/maven-plugin/build-image.html)
* [Testcontainers](https://java.testcontainers.org/)
* [Spring Boot Testcontainers support](https://docs.spring.io/spring-boot/4.0.2/reference/testing/testcontainers.html#testing.testcontainers)
* [Testcontainers Postgres Module Reference Guide](https://java.testcontainers.org/modules/databases/postgres/)
* [Testcontainers Kafka Modules Reference Guide](https://java.testcontainers.org/modules/kafka/)
* [Testcontainers Grafana Module Reference Guide](https://java.testcontainers.org/modules/grafana/)
* [OpenTelemetry](https://docs.spring.io/spring-boot/4.0.2/reference/actuator/observability.html#actuator.observability.opentelemetry)

### Руководства

Следующие руководства наглядно демонстрируют использование некоторых возможностей:

* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
* [Using Spring Data JDBC](https://github.com/spring-projects/spring-data-examples/tree/main/jdbc/basics)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Messaging with Redis](https://spring.io/guides/gs/messaging-redis/)
* [Building a GraphQL service](https://spring.io/guides/gs/graphql-server/)

### Дополнительные ссылки

Эти дополнительные материалы также могут быть полезны:

* [Various sample apps using Spring gRPC](https://github.com/spring-projects/spring-grpc/tree/main/samples)

### Поддержка Testcontainers

В этом проекте
используется [Testcontainers во время разработки](https://docs.spring.io/spring-boot/4.0.2/reference/features/dev-services.html#features.dev-services.testcontainers).

Testcontainers настроен на использование следующих Docker-образов:

* [`grafana/otel-lgtm:latest`](https://hub.docker.com/r/grafana/otel-lgtm)
* [`apache/kafka-native:4.1.1`](https://hub.docker.com/r/apache/kafka-native)
* [`postgres:17-alpine`](https://hub.docker.com/_/postgres)
* [`redis:8.2-alpine`](https://hub.docker.com/_/redis)

Проверьте теги используемых образов и убедитесь, что они совпадают с теми, которые используются в production.

### Переопределение Maven Parent

В силу особенностей Maven элементы наследуются из родительского POM в POM проекта.
Хотя большая часть наследования допустима, некоторые нежелательные элементы, такие как `<license>` и `<developers>`, также наследуются от родителя.
Чтобы предотвратить это, POM проекта содержит пустые переопределения для этих элементов.
Если вы вручную переключитесь на другой родительский POM и захотите сохранить наследование, вам нужно удалить эти переопределения.
