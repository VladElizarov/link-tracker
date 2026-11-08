# Нагрузочный тест Scrapper API

Сравнивает скорость `GET /links` (и `POST` / `DELETE /links`) в трёх режимах кэша:
`NONE` (без кэша), `VALKEY` (кэш в Valkey), `CLIENT_SIDE` (Valkey + Client Side Caching).

Нагрузочный клиент — обычная Java-программа `ru.tbank.load.LoadTest` (без сторонних библиотек):
N потоков-«пользователей» шлют запросы без пауз, на 100 чтений приходится 1 изменение
(добавить ссылку, в следующий раз — удалить её). Пользователи стартуют постепенно (ramp-up),
статистика считается только за основной этап (stage).

## 1. Подготовка (один раз)

```bash
docker compose up -d
mvn clean package -DskipTests

# отдельная база для нагрузки, чтобы не испортить рабочие данные
docker compose exec -T postgresql psql -U postgres -c "CREATE DATABASE scrapper_load;"
docker compose run --rm liquibase-migrations --searchPath=/liquibase/changelog --changelog-file=master.xml \
  --driver=org.postgresql.Driver --url=jdbc:postgresql://postgresql:5432/scrapper_load \
  --username=postgres --password=postgres update

# 1000 чатов x 100 ссылок = 100 000 ссылок
docker compose exec -T postgresql psql -U postgres -d scrapper_load < load-tests/seed.sql
```

В Git Bash перед командами с путями внутри контейнера может понадобиться `export MSYS_NO_PATHCONV=1`.

## 2. Запуск

```bash
bash load-tests/run-all.sh            # ramp-up 60 с, stage 300 с, все три режима (~20 минут)
bash load-tests/run-all.sh 5 20 VALKEY  # быстрая проверка одного режима
```

Скрипт для каждого режима: очищает Valkey, запускает scrapper с `app.cache.mode=<режим>` на базе `scrapper_load`
(планировщик выключен, чтобы не ходить в GitHub), прогоняет `LoadTest` и останавливает scrapper.
Число потоков по умолчанию — 2 × число ядер (можно задать `THREADS=16 bash load-tests/run-all.sh`).

Результаты: `load-tests/results/<РЕЖИМ>.md` (таблица), `scrapper-<РЕЖИМ>.log` (лог приложения).
Итоговый отчёт с выводами: [docs/task6-load-report.md](../docs/task6-load-report.md).
