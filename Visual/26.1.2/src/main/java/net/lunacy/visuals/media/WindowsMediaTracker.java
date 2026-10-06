package net.lunacy.visuals.media;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.W32APIOptions;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Ultra-lightweight background daemon tracking currently playing media on Windows (Spotify, Yandex Music, browsers).
 * Uses direct Win32 API calls via JNA without spawning external processes (powershell), with 0% CPU impact.
 */
public final class WindowsMediaTracker {
    private static final WindowsMediaTracker INSTANCE = new WindowsMediaTracker();
    private static final MediaTrack EMPTY = new MediaTrack("", "", "");

    private final AtomicReference<MediaTrack> currentTrack = new AtomicReference<>(EMPTY);
    private Thread workerThread;
    private volatile boolean running;
    private static boolean jnaAvailable = true;

    public interface User32 extends Library {
        User32 INSTANCE = load();

        private static User32 load() {
            try {
                return Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);
            } catch (Throwable t) {
                jnaAvailable = false;
                return null;
            }
        }

        interface WNDENUMPROC extends Callback {
            boolean callback(Pointer hWnd, Pointer arg);
        }

        boolean EnumWindows(WNDENUMPROC lpEnumFunc, Pointer arg);
        int GetWindowTextW(Pointer hWnd, char[] lpString, int nMaxCount);
        boolean IsWindowVisible(Pointer hWnd);
        void keybd_event(byte bVk, byte bScan, int dwFlags, Pointer dwExtraInfo);
    }

    private WindowsMediaTracker() {}

    public static WindowsMediaTracker get() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        workerThread = new Thread(this::pollLoop, "Lunacy-MediaTracker");
        workerThread.setDaemon(true);
        workerThread.setPriority(Thread.MIN_PRIORITY);
        workerThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    public MediaTrack getCurrentTrack() {
        return currentTrack.get();
    }

    /** Toggle Play / Pause in Spotify, Yandex Music, YouTube, and media players. */
    public static void playPause() {
        sendKey((byte) 0xB3); // VK_MEDIA_PLAY_PAUSE
    }

    /** Skip to next track. */
    public static void nextTrack() {
        sendKey((byte) 0xB0); // VK_MEDIA_NEXT_TRACK
    }

    /** Return to previous track. */
    public static void previousTrack() {
        sendKey((byte) 0xB1); // VK_MEDIA_PREV_TRACK
    }

    private static void sendKey(byte vkCode) {
        if (!jnaAvailable || User32.INSTANCE == null) return;
        try {
            User32.INSTANCE.keybd_event(vkCode, (byte) 0, 0, Pointer.NULL);
            User32.INSTANCE.keybd_event(vkCode, (byte) 0, 2, Pointer.NULL); // KEYEVENTF_KEYUP = 2
        } catch (Throwable ignored) {}
    }

    private void pollLoop() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win") || !jnaAvailable || User32.INSTANCE == null) {
            return;
        }

        char[] buffer = new char[512];
        final MediaTrack[] foundTrack = new MediaTrack[1];

        User32.WNDENUMPROC callback = (hWnd, arg) -> {
            try {
                if (!User32.INSTANCE.IsWindowVisible(hWnd)) return true;
                int len = User32.INSTANCE.GetWindowTextW(hWnd, buffer, 512);
                if (len > 0) {
                    String title = new String(buffer, 0, len).trim();
                    MediaTrack t = parseTitle(title);
                    if (t != null) {
                        foundTrack[0] = t;
                        return false; // Found active music track! Stop search
                    }
                }
            } catch (Throwable ignored) {}
            return true;
        };

        while (running) {
            try {
                foundTrack[0] = null;
                User32.INSTANCE.EnumWindows(callback, Pointer.NULL);
                if (foundTrack[0] != null) {
                    currentTrack.set(foundTrack[0]);
                } else {
                    currentTrack.set(EMPTY);
                }
            } catch (Throwable ignored) {
                currentTrack.set(EMPTY);
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static MediaTrack parseTitle(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();

        String lower = t.toLowerCase();
        // Filter non-media windows and IDE/system windows
        if (lower.contains("собираем музыку") || lower.equals("моя волна") || lower.equals("главное")
                || lower.equals("радио") || lower.equals("плейлист дня") || lower.equals("чарт")
                || lower.equals("коллекция") || lower.equals("настройки") || lower.equals("яндекс музыка")
                || lower.equals("яндекс.музыка") || lower.equals("yandex music")
                || lower.equals("spotify") || lower.equals("spotify free") || lower.equals("spotify premium")
                || lower.equals("default") || lower.startsWith("lunacy") || lower.startsWith("minecraft")
                || lower.contains("visual studio") || lower.contains("intellij") || lower.contains("discord")
                || lower.contains("telegram") || lower.contains("explorer") || lower.contains("settings")
                || lower.contains("task manager") || lower.contains("command prompt") || lower.contains("powershell")) {
            return null;
        }

        if (t.startsWith("Яндекс Музыка — ") || t.startsWith("Яндекс Музыка - ")) {
            String clean = t.substring(16).trim();
            if (isBlacklisted(clean)) return null;
            return parseSongAndArtist(clean, "Yandex Music");
        }

        if (t.contains(" - Яндекс Музыка") || t.contains(" — Яндекс Музыка")) {
            String clean = t.replace(" - Яндекс Музыка", "").replace(" — Яндекс Музыка", "").trim();
            if (isBlacklisted(clean)) return null;
            return parseSongAndArtist(clean, "Yandex Music");
        }

        if (t.contains(" - YouTube Music")) {
            String clean = t.replace(" - YouTube Music", "").trim();
            if (isBlacklisted(clean)) return null;
            return parseSongAndArtist(clean, "YouTube Music");
        }

        if (t.contains(" - Spotify")) {
            String clean = t.replace(" - Spotify", "").trim();
            if (isBlacklisted(clean)) return null;
            return parseSongAndArtist(clean, "Spotify");
        }

        if (t.endsWith(" | ВКонтакте") && (t.contains(" - ") || t.contains(" — "))) {
            String clean = t.replace(" | ВКонтакте", "").trim();
            if (isBlacklisted(clean)) return null;
            return parseSongAndArtist(clean, "VK Music");
        }

        // Spotify desktop window title is literally "Artist - Track" when playing
        if (t.contains(" - ") || t.contains(" — ")) {
            return parseSongAndArtist(t, "Media");
        }

        return null;
    }

    private static boolean isBlacklisted(String s) {
        if (s == null || s.isBlank()) return true;
        String l = s.toLowerCase();
        return l.contains("собираем музыку") || l.equals("моя волна") || l.equals("главное")
                || l.equals("радио") || l.equals("плейлист дня") || l.equals("чарт")
                || l.equals("яндекс музыка") || l.equals("yandex music");
    }

    private static boolean isAppName(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase().trim();
        return lower.equals("яндекс музыка") || lower.equals("yandex music")
                || lower.equals("яндекс.музыка") || lower.equals("spotify")
                || lower.equals("youtube music") || lower.equals("vk music")
                || lower.equals("вконтакте") || lower.equals("media");
    }

    private static MediaTrack parseSongAndArtist(String text, String source) {
        String sep = text.contains(" — ") ? " — " : (text.contains(" - ") ? " - " : null);
        if (sep != null) {
            String[] parts = text.split(sep, 2);
            if (parts.length == 2) {
                String part1 = parts[0].trim();
                String part2 = parts[1].trim();
                if (isAppName(part1)) {
                    return new MediaTrack(part2, "", source);
                }
                if (isAppName(part2)) {
                    return new MediaTrack(part1, "", source);
                }
                // Spotify uses "Artist - Track"
                if ("Spotify".equalsIgnoreCase(source)) {
                    return new MediaTrack(part2, part1, source);
                }
                // Yandex Music, VK, and other Russian players use "Track — Artist"
                // part1 is Track Title (имя произведения), part2 is Artist
                return new MediaTrack(part1, part2, source);
            }
        }
        return new MediaTrack(text, "", source);
    }

    public record MediaTrack(String title, String artist, String source) {
        public boolean isPlaying() {
            return title != null && !title.isBlank() && !"NONE".equalsIgnoreCase(title)
                    && !title.toLowerCase().contains("собираем музыку")
                    && !title.equalsIgnoreCase("моя волна")
                    && !title.equalsIgnoreCase("главное")
                    && !title.equalsIgnoreCase("радио");
        }

        public boolean isSpotify() {
            return "Spotify".equalsIgnoreCase(source);
        }

        public boolean isYandex() {
            return source != null && source.toLowerCase().contains("yandex");
        }

        public String app() {
            return source != null ? source : "Media";
        }

        public String shortTitle() {
            if (!isPlaying()) return "";
            return title;
        }

        public String fullTitle() {
            if (!isPlaying()) return "";
            if (artist != null && !artist.isBlank() && !artist.equalsIgnoreCase(title) && !isAppName(artist)) {
                return title + " - " + artist;
            }
            return title;
        }

        public String formatted() {
            return shortTitle();
        }
    }
}