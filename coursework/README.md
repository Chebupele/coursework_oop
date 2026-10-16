# Курсовая работа

Консольный агрегатор данных из Open-Meteo, Open Library и DummyJSON. Поддерживает последовательный и параллельный опрос, периодический запуск, сохранение и чтение JSON/CSV.

Требуется JDK 17–24.

## Сборка

```powershell
.\gradlew.bat build
```

## Запуск

Интерактивное меню:

```powershell
.\gradlew.bat run
```

Один параллельный опрос трёх API:

```powershell
.\gradlew.bat run --args="--apis weather,books,products --format json --threads 2 --rounds 1"
```

Последовательный опрос:

```powershell
.\gradlew.bat run --args="--execution sequential --format csv"
```

Периодический опрос и дозапись:

```powershell
.\gradlew.bat run --args="--mode append --threads 2 --interval 60 --rounds 3"
```

Чтение сохранённых записей:

```powershell
.\gradlew.bat run --args="--read --file data/records.json"
.\gradlew.bat run --args="--read --file data/records.json --source weather"
```

Настройки источников находятся в src/main/resources/apis.json. Внешний файл можно указать через --config. Полная справка доступна по --help.

В IntelliJ IDEA откройте эту папку как Gradle-проект и запустите ru.oop.aggregator.Main. Для интерактивного режима оставьте Program arguments пустым.

## Тесты

```powershell
.\gradlew.bat test jacocoTestReport
```

Unit-тесты не обращаются к сети. Задача build проверяет минимальное покрытие строк 70%. Отчёт JaCoCo создаётся в build/reports/jacoco/test/html/index.html.
