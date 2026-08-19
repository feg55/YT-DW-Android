package io.github.ytdw.android.ui

import io.github.ytdw.android.domain.model.LanguagePreference
import java.util.Locale

data class UiStrings(
    val appName: String, val analyzeTab: String, val reviewTab: String, val downloadTab: String,
    val settings: String, val urls: String, val analyze: String, val cancel: String,
    val audio: String, val video: String, val quality: String, val analyzing: String,
    val nothingFound: String, val selected: String, val title: String, val artist: String,
    val albumArtist: String, val album: String, val track: String, val start: String,
    val pause: String, val retryFailed: String, val removeCompleted: String, val clearAll: String,
    val open: String, val queueEmpty: String, val progress: String, val speed: String, val eta: String,
    val close: String, val theme: String, val language: String, val dark: String, val light: String,
    val system: String, val original: String, val russian: String, val english: String,
    val useChannelArtist: String, val channelAlbumArtist: String, val removeChannel: String,
    val removeLabels: String, val playlistAlbum: String, val playlistTrack: String,
    val cleanedFilename: String, val embedCover: String, val squareCover: String,
    val separateCover: String, val storeUrl: String, val storeYear: String, val skipArchive: String,
    val outputHint: String, val confirmClear: String, val confirm: String, val history: String,
)

fun uiStrings(preference: LanguagePreference): UiStrings {
    val russian = isRussian(preference)
    return if (russian) UiStrings(
        "YT-DW", "1. Анализ", "2. Проверка", "3. Загрузка", "Настройки",
        "URL, по одному в строке", "Анализировать", "Отмена", "Аудио", "Видео", "Качество",
        "Анализ…", "Ничего не найдено", "Выбрано", "Название", "Исполнитель",
        "Исполнитель альбома", "Альбом", "Трек", "Запустить", "Пауза", "Повторить ошибки",
        "Удалить завершённые", "Очистить очередь, историю и архив", "Открыть", "Очередь пуста",
        "Прогресс", "Скорость", "Осталось", "Закрыть", "Тема", "Язык", "Тёмная", "Светлая",
        "Системная", "Оригинальная", "Русский", "English", "Канал как исполнитель",
        "Канал как исполнитель альбома", "Удалять канал из названия", "Удалять служебные метки",
        "Плейлист как альбом", "Номер в плейлисте как трек", "Очищенное имя файла",
        "Встраивать обложку", "Квадратная обрезка", "Сохранять отдельный JPEG",
        "Сохранять исходный URL", "Сохранять год", "Пропускать загруженное ранее",
        "Папки назначения", "Медиафайлы удалены не будут. Очистить состояние загрузок?",
        "Очистить", "История",
    ) else UiStrings(
        "YT-DW", "1. Analyze", "2. Review", "3. Download", "Settings",
        "URLs, one per line", "Analyze", "Cancel", "Audio", "Video", "Quality", "Analyzing…",
        "Nothing found", "Selected", "Title", "Artist", "Album artist", "Album", "Track",
        "Start", "Pause", "Retry failed", "Remove completed", "Clear queue, history and archive",
        "Open", "Queue is empty", "Progress", "Speed", "ETA", "Close", "Theme", "Language",
        "Dark", "Light", "System", "Original", "Русский", "English", "Use channel as artist",
        "Use channel as album artist", "Remove channel from title", "Remove trailing labels",
        "Use playlist as album", "Use playlist index as track", "Use cleaned filename",
        "Embed cover", "Crop cover to square", "Save separate JPEG", "Store source URL",
        "Store upload year", "Skip previously downloaded", "Output folders",
        "Downloaded media will not be deleted. Clear download state?", "Clear", "History",
    )
}

fun isRussian(preference: LanguagePreference): Boolean =
    preference == LanguagePreference.RUSSIAN ||
        preference == LanguagePreference.SYSTEM && Locale.getDefault().language == "ru"

fun localizedError(category: String?, fallback: String, preference: LanguagePreference): String {
    if (!isRussian(preference)) return fallback
    return when (category?.lowercase()) {
        "authentication_required" -> "Для этого медиа требуется вход на сайте-источнике."
        "source_rejected" -> "Видеосервер отклонил скачивание (HTTP 403). Попробуйте без VPN или через другую сеть."
        "network" -> "Ошибка сети. Проверьте подключение и повторите попытку."
        "disk_full" -> "Недостаточно места в хранилище."
        "unsupported" -> "Этот URL не поддерживается."
        "metadata" -> "Не удалось записать или проверить метаданные файла."
        "cancelled" -> "Отменено"
        else -> fallback
    }
}

fun localizedPhase(value: String, preference: LanguagePreference): String {
    if (!isRussian(preference)) return value
    return when (value) {
        "Downloading", "Downloading media" -> "Загрузка"
        "Preparing downloader" -> "Подготовка загрузчика"
        "Connecting to source" -> "Подключение к источнику"
        "Retrying via IPv4" -> "Повтор через IPv4"
        "Retrying with default source client" -> "Повтор через стандартный клиент источника"
        "Processing" -> "Обработка"
        "Writing metadata" -> "Запись метаданных"
        "Merging" -> "Объединение потоков"
        "Converting" -> "Конвертация"
        "Completed" -> "Завершено"
        "Failed" -> "Ошибка"
        "Cancelled" -> "Отменено"
        "Already downloaded" -> "Уже загружено"
        else -> value
    }
}

fun localizedStatus(value: String, preference: LanguagePreference): String {
    if (!isRussian(preference)) return value.lowercase()
    return when (value) {
        "PENDING" -> "ожидание анализа"
        "ANALYZING" -> "анализ"
        "READY" -> "готово"
        "DOWNLOADING" -> "загрузка"
        "PROCESSING" -> "обработка"
        "COMPLETED" -> "завершено"
        "SKIPPED" -> "пропущено"
        "CANCELLED" -> "отменено"
        "FAILED" -> "ошибка"
        else -> value.lowercase()
    }
}
