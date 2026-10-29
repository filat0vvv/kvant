# kvantGram для Android

Неофициальный клиент Telegram с собственными названием и иконкой.
Пакет: `com.kvant.messenger`. Первая конфигурация сборки — **arm64-v8a**.

![Логотип kvantGram](assets/kvantgram-logo.png)

## Что подготовлено

- Имя `kvantGram` под иконкой, в манифестах и локальных строках приложения.
- Бирюзовый знак «К» с орбитой; обычная и адаптивная иконки Android.
- Отдельный application ID. Внутренние Java-пакеты Telegram сохранены.
- Один сценарий сборки вместо двух продублированных цепочек.
- Перед Gradle собираются libvpx, FFmpeg и BoringSSL. Отсутствующий
  `ffmpeg/build/armeabi-v7a/lib/libswscale.a` останавливал прежний запуск.
  Новая конфигурация ограничивает библиотеки и APK архитектурой arm64.
- Постоянная подпись из GitHub Secrets; общие демонстрационные API-ключи удалены.
- Проверка подписи, названия, package и архитектуры готового APK.
- Унаследованный авто-прокси вынесен без изменения логики в
  `assets/AutoProxyManager.java`. Его работа на устройстве не проверена.

Используется та же база Telegram-FOSS, закреплённая на
[`10da5406ed92d30c6add3b25d40b2b3b6995d873`](https://github.com/Telegram-FOSS-Team/Telegram-FOSS/commit/10da5406ed92d30c6add3b25d40b2b3b6995d873).
В ней указана версия **10.14.3**, исходники относятся к 2024 году. Перенос на
современный Telegram ещё не выполнен. Windows EXE этим сценарием не собирается.

## Ключи для первой сборки

Откройте [Settings → Secrets and variables → Actions](https://github.com/filat0vvv/kvant/settings/secrets/actions)
и добавьте **Repository secrets**:

| Название | Значение |
| --- | --- |
| `TELEGRAM_API_ID` | Собственный API ID с [my.telegram.org/apps](https://my.telegram.org/apps) |
| `TELEGRAM_API_HASH` | Соответствующий API Hash |
| `ANDROID_KEYSTORE_BASE64` | Постоянный keystore в Base64, одной строкой |
| `ANDROID_KEYSTORE_PASSWORD` | Пароль keystore |
| `ANDROID_KEY_ALIAS` | Alias ключа, например `kvantgram` |
| `ANDROID_KEY_PASSWORD` | Пароль ключа |

Если запись уже существует, повторно добавлять её не нужно. GitHub не показывает
существующие значения. Проверка сборки выводит только названия отсутствующих записей.
Не добавляйте значения в исходники, README, issues или PR.

Если постоянного ключа подписи ещё нет, создайте его **один раз** через `keytool`
из установленной Java / Android Studio. На Windows PowerShell:

```powershell
& 'D:\Android Studio\jbr\bin\keytool.exe' -genkeypair -keystore kvantgram-release.jks -storetype JKS -alias kvantgram -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=kvantGram'
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path '.\kvantgram-release.jks'))) | Set-Content -NoNewline -Encoding ascii keystore-base64.txt
```

Пароли вводятся по запросу `keytool`. Если пароли совпадают, добавьте одинаковое
значение в обе записи с паролями. Содержимое `keystore-base64.txt` поместите
в `ANDROID_KEYSTORE_BASE64`. Сохраните резервную копию keystore и паролей вне репозитория.
Если приложение уже распространялось, используйте прежний ключ для совместимости обновлений.

## Как получить APK

1. Добавьте Secrets из таблицы.
2. Откройте [Actions](https://github.com/filat0vvv/kvant/actions) → **Сборка kvantGram APK** → **Run workflow**.
3. Выберите ветку с этими изменениями. Оставьте `kvantGram` и `com.kvant.messenger`.
4. После успешного запуска скачайте **kvantGram-Android-APK** в разделе Artifacts.
   Внутри находятся `kvantGram-arm64.apk`, контрольная сумма и сведения об APK.

Если новая ветка ещё не появляется в меню Actions, сначала перенесите изменения
в основную ветку через PR. Установка APK и вход в аккаунт проверяются на телефоне.

Унаследованное автообновление Telegram-FOSS отключено: оно ведёт к другому приложению.
Загрузка облачных переводов отключена, чтобы она не восстановила прежнее AppName;
встроенные переводы сохранены.

## Проверка и исходники

Локально проверены применение патча к указанному коммиту, XML, размеры иконок,
application ID, arm64-фильтры, конфигурация подписи и синтаксис workflow/Python.
Полная компиляция Android и работа на телефоне **пока не проверены**.

База: [Telegram-FOSS](https://github.com/Telegram-FOSS-Team/Telegram-FOSS),
лицензия GPL-2.0-or-later; текст сохранён в `LICENSE`.
Артефакт успешной сборки включает архив этого репозитория, diff исходников
и номера коммитов базы и подмодулей. Новые Java/XML/PNG ресурсы включаются в diff;
API credentials и ключ подписи в архивы не входят. Сохраняйте соответствующие
исходники вместе с распространяемой сборкой.
