# Чек-лист GitHub Release

## Однократная настройка

1. Создайте постоянный ключ подписи и сохраните минимум две резервные копии вне репозитория:

   ```bash
   keytool -genkeypair -v -keystore release-key.jks -alias release -keyalg RSA -keysize 4096 -validity 10000
   ```

2. На GitHub откройте `Settings` → `Environments` → `New environment`, создайте окружение `release` и при желании включите обязательное ручное подтверждение.
3. Добавьте в окружение `release` четыре секрета:

   - `ANDROID_KEYSTORE_BASE64` — содержимое `release-key.jks` в Base64 одной строкой;
   - `ANDROID_KEYSTORE_PASSWORD` — пароль хранилища;
   - `ANDROID_KEY_ALIAS` — alias ключа;
   - `ANDROID_KEY_PASSWORD` — пароль ключа.

   Linux/macOS:

   ```bash
   base64 -w 0 release-key.jks
   ```

   PowerShell:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("release-key.jks"))
   ```

Никогда не добавляйте ключ или его Base64-представление в Git, issue, логи либо файлы workflow.

## До сборки

- [ ] Рабочее дерево чистое; в нём нет `local.properties`, keystore, `keystore.properties`, cookie, токенов и логов с персональными данными.
- [ ] `versionCode` увеличен, `versionName` совпадает с будущим тегом.
- [ ] Версия и SHA-256 `yt-dlp` в `app/build.gradle.kts` проверены.
- [ ] `CHANGELOG.md` содержит раздел будущей версии и правильную дату.
- [ ] Выполнено `./gradlew clean check lintDebug assembleDebug assembleDebugAndroidTest`.
- [ ] Настроен постоянный release-keystore через локальный `keystore.properties`.

## Релизная сборка

```bash
./gradlew assembleRelease bundleRelease -PreleaseAbiSplits=true
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-universal-release.apk
```

- [ ] Сертификат совпадает с предыдущим публичным релизом и не является debug-сертификатом.
- [ ] APK установлен на чистое устройство.
- [ ] Обновление поверх предыдущего публичного APK прошло без ошибки подписи.
- [ ] Анализ ссылки и реальное скачивание аудио/видео проверены на устройстве.

## Исходники и лицензии

- [ ] Создан подписанный или аннотированный тег, например `v0.1.3`, указывающий на точный исходный коммит APK.
- [ ] В релизе доступны автоматические GitHub-архивы исходников этого тега.
- [ ] Рядом с APK приложены архивы исходников `youtubedl-android 0.18.1` и `yt-dlp 2026.07.04` либо их проверенные зеркала.
- [ ] В описании релиза есть ссылка на `THIRD_PARTY_NOTICES.md`.
- [ ] APK содержит `assets/legal/LICENSE`, `NOTICE` и `THIRD_PARTY_NOTICES.md`.

## Публикация

Рекомендуемый путь:

```bash
git status
git add .
git commit -m "Prepare v0.1.3 release"
git push origin main
git tag -a v0.1.3 -m "YT-DW v0.1.3"
git push origin v0.1.3
```

Тег запускает `.github/workflows/release.yml`. Workflow:

1. проверяет совпадение тега и `versionName`;
2. выполняет тесты и lint;
3. собирает подписанные APK для `arm64-v8a`, `armeabi-v7a`, `x86_64`, универсальный APK и AAB;
4. проверяет сертификаты APK;
5. прикладывает исходники сторонних компонентов и `SHA256SUMS.txt`;
6. создаёт черновик GitHub Release.

После успешного workflow откройте черновик релиза, скачайте APK `arm64-v8a`, повторно проверьте SHA-256 и установите его на телефон. Только после smoke-теста нажмите **Publish release**.

- [ ] Для APK опубликован SHA-256.
- [ ] В тексте релиза указано: «Программа предоставляется как есть, без гарантий. Пользователь отвечает за соблюдение правил сервисов, авторских прав и местного законодательства».
- [ ] Keystore и его резервная копия сохранены вне GitHub.

Нельзя обещать полное отсутствие юридических претензий: GPL ограничивает гарантии и ответственность только в пределах применимого законодательства и не отменяет авторские права на скачиваемые материалы, условия сайтов, патенты и требования конкретной страны.
