# LinkTracker

Telegram-бот, который следит за ссылками и присылает уведомления, когда там появляется что-то новое:

- **GitHub** (`https://github.com/{owner}/{repo}`): новый Issue или Pull Request;
- **StackOverflow** (`https://stackoverflow.com/questions/{id}`): новый ответ или комментарий.

Проект состоит из двух приложений:

| Модуль     | Порт | Что делает                                                                          |
|------------|------|-------------------------------------------------------------------------------------|
| `bot`      | 8080 | общается с пользователем в Telegram, получает уведомления от scrapper из Kafka (или `POST /updates`) |
| `scrapper` | 8081 | хранит ссылки в PostgreSQL, по расписанию проверяет их и шлёт уведомления боту через Kafka; ответ `GET /links` кэширует в Valkey |
| `common`   | -    | общие DTO (контракт между bot и scrapper)                                           |
| `integration-tests` | - | только тесты: полный путь Scrapper → Kafka → Bot → Telegram                   |
| `load-tests` | - | нагрузочный тест `GET/POST /links` с кэшем и без ([load-tests/README.md](load-tests/README.md)) |

```
scrapper ──(outbox в PostgreSQL)──► Kafka: link-updates ──► bot ──► Telegram
                                                     │
                                         ошибка ──► link-updates-dlq
```

## Что нужно

- Java 21+ (проверено на JDK 25)
- Maven 3.9+
- Docker Desktop (PostgreSQL и Kafka, а также тесты с Testcontainers). Нужно ~3 ГБ памяти для Docker
- Токен Telegram-бота (получить у [@BotFather](https://t.me/BotFather) командой `/newbot`)

## 1. Настройка `.env`

Секреты хранятся в файле `.env`, который **не попадает в git** (он прописан в `.gitignore`).

```bash
cp .env.example .env
# откройте .env и вставьте свой токен: TELEGRAM_TOKEN=1234567890:AA...
```

Что можно указать в `.env`:

| Переменная                  | Обязательна | Зачем                                                     |
|-----------------------------|-------------|-----------------------------------------------------------|
| `TELEGRAM_TOKEN`            | да          | токен бота                                                |
| `GITHUB_TOKEN`              | нет         | без него GitHub разрешает только 60 запросов в час        |
| `STACKOVERFLOW_KEY`         | нет         | ключ приложения StackExchange (повышает лимит запросов)  |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | нет | логин/пароль БД (по умолчанию `postgres`/`postgres`) |
| `APP_DATABASE_ACCESS_TYPE`  | нет         | `SQL` (по умолчанию) или `ORM`                             |
| `APP_SCHEDULER_BATCH_SIZE`  | нет         | сколько ссылок брать из БД за раз, от 50 до 500 (по умолчанию 100) |
| `APP_SCHEDULER_THREADS`     | нет         | сколько потоков проверяют ссылки (по умолчанию 4)         |
| `APP_SCHEDULER_INTERVAL`    | нет         | пауза между проверками ссылок (по умолчанию `60s`)        |
| `APP_MESSAGE_TRANSPORT`     | нет         | как scrapper шлёт уведомления боту: `KAFKA` (по умолчанию) или `HTTP` |
| `KAFKA_BOOTSTRAP_SERVERS`   | нет         | адреса брокеров (по умолчанию `localhost:29092,localhost:29093,localhost:29094`) |
| `APP_KAFKA_RETRY_MAX_ATTEMPTS` | нет      | сколько раз бот пробует обработать сообщение, прежде чем отправить его в DLQ (по умолчанию 3) |
| `APP_CACHE_MODE`            | нет         | кэш `GET /links`: `VALKEY` (по умолчанию), `CLIENT_SIDE` или `NONE` |
| `APP_CACHE_TTL`             | нет         | сколько хранить ответ в кэше (по умолчанию `10m`)          |

## 2. Инфраструктура (PostgreSQL + Kafka)

```bash
docker compose up -d
```

Что поднимется:

| Сервис                 | Порт снаружи        | Что это                                                     |
|------------------------|---------------------|-------------------------------------------------------------|
| `postgresql`           | 5433                | база scrapper (5432 часто занят локальным PostgreSQL)       |
| `liquibase-migrations` | -                   | применяет миграции из `migrations/` и завершается            |
| `kafka-1..3`           | 29092, 29093, 29094 | кластер Kafka из 3 брокеров, режим KRaft (без Zookeeper)     |
| `kafka-ui`             | 8090                | веб-интерфейс Kafka: http://localhost:8090                   |
| `valkey-1..3`          | 6381, 6382, 6383    | Valkey: основной узел и 2 реплики (кэш `GET /links`)          |
| `valkey-sentinel-1..3` | 26381, 26382, 26383 | Sentinel: при падении основного узла Valkey назначают новый   |
| `valkey-net`           | -                   | общая сеть узлов Valkey (через неё проброшены порты выше)     |

Топики `link-updates` и `link-updates-dlq` создают сами приложения при старте.
Остановить: `docker compose down` (данные останутся в томах), удалить вместе с данными: `docker compose down -v`.

## 3. Запуск

Оба приложения запускаются «просто из IDE»: классы `ru.tbank.bot.BotApplication` и
`ru.tbank.scrapper.ScrapperApplication`. Или из корня проекта в двух терминалах:

```bash
mvn -pl scrapper -am spring-boot:run
mvn -pl bot -am spring-boot:run
```

Или после сборки (запускаемые jar-файлы - с суффиксом `-exec`):

```bash
mvn clean package -DskipTests
java -jar scrapper/target/scrapper-1.0-SNAPSHOT-exec.jar
java -jar bot/target/bot-1.0-SNAPSHOT-exec.jar
```

Без Kafka (только HTTP): запустить оба приложения с `APP_MESSAGE_TRANSPORT=HTTP` (в `.env` или переменной окружения).
Без Valkey: запустить scrapper с `APP_CACHE_MODE=NONE` (иначе при старте он подключается к Valkey через Sentinel).

Файл `.env` ищется в текущей папке и в папке на уровень выше, поэтому всё это работает из корня проекта.

## 4. Тесты

```bash
mvn clean test
```

Нужен запущенный Docker: тесты сами поднимают PostgreSQL и Kafka через Testcontainers
(`docker compose` для тестов не нужен). GitHub, StackOverflow и Telegram в тестах подменяются WireMock.

### Проверка интеграции Scrapper → Kafka → Bot

Один тест, который поднимает PostgreSQL и Kafka, запускает scrapper и bot целиком и проверяет,
что новый Issue из «GitHub» доходит до «Telegram» пользователя через Kafka:

```bash
mvn -pl integration-tests -am test -Dtest=ScrapperKafkaBotIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Или в IDE: `integration-tests/src/test/java/ru/tbank/integration/ScrapperKafkaBotIntegrationTest.java` → Run.

Другие тесты Kafka: `KafkaConsumerTest` (bot: обработка, повторы, DLQ), `KafkaMessageSenderTest` (scrapper: формат сообщения, outbox).

## Как пользоваться ботом

| Команда            | Что делает                                                   |
|--------------------|--------------------------------------------------------------|
| `/start`           | регистрация                                                  |
| `/help`            | список команд                                                |
| `/track`           | начать отслеживать ссылку (бот спросит ссылку, теги, фильтры) |
| `/untrack <ссылка>`| перестать отслеживать                                       |
| `/list [тег]`      | список отслеживаемых ссылок (можно по тегу)                  |
| `/cancel`          | отменить диалог `/track`                                     |

Пример уведомления:

```
Обновление по ссылке https://github.com/user/repo
Новый Issue
Название: Падает сборка
Автор: octocat
Создан: 07.11.2026 10:05 (UTC)
Описание: После обновления не собирается ...   (первые 200 символов)
```

Если Telegram не принимает сообщение, бот пробует ещё 2 раза (всего 3 попытки), потом кладёт его
в топик `link-updates-dlq` (Dead Letter Queue). Его можно посмотреть в Kafka UI: http://localhost:8090.

Если ссылку не удалось проверить (репозиторий удалён, API недоступен), придёт отчёт:
`Отчёт: не удалось проверить ссылку. Причина: ...` (один раз, пока ссылка не заработает снова).

## Настройки планировщика (`scrapper/src/main/resources/application.yaml`)

```yaml
app:
  scheduler:
    enable: true      # включён ли планировщик
    interval: 60s     # пауза между проверками
    batch-size: 100   # размер батча, от 50 до 500
    threads: 4        # сколько потоков проверяют один батч
```
