package org.eu.sager.netmusicradio.client.util;

import org.eu.sager.netmusicradio.NetMusicRadioAddon;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * BigMegaphoneUtil 的代理类
 * <p>
 * 提供放宽的 URL 验证逻辑，支持：
 * - 标准音频扩展名 (.mp3, .ogg, .flac 等)
 * - Shoutcast/Icecast 流媒体（无扩展名）
 * - 常见流媒体服务平台
 * - HTTP 直链
 */
public final class BigMegaphoneUtilProxy {
    private static final Method remoteIsValid;

    private static final Set<String> SHOUTCAST_PORTS = Set.of(
            "8000", "8080", "8888", "9000", "10000", "1234", "8001", "8002",
            "8123", "8300", "80", "443", "8081"
    );

    private static final Set<String> STREAMING_SERVICE_DOMAINS = Set.of(
            "shoutcast.com", "icecast.org", "radio", "stream", "live",
            "listen", "broadcast", "music", "fm", "am"
    );

    private static final Pattern SHOUTCAST_PATH_PATTERN = Pattern.compile("^/\\d{2,}$");
    private static final Pattern STREAM_ID_PATTERN = Pattern.compile("^/[^./]+$");

    static {
        Method m = null;
        try {
            Class<?> cls = Class.forName("com.github.tartaricacid.netmusic.util.BigMegaphoneUtil");
            m = cls.getMethod("isValidStreamUrl", String.class);
        } catch (Throwable t) {
            NetMusicRadioAddon.LOGGER.debug("Could not find remote isValidStreamUrl method", t);
        }
        remoteIsValid = m;
    }

    private BigMegaphoneUtilProxy() {
    }

    /**
     * 验证是否为有效的流媒体 URL
     * <p>
     * 策略：
     * 1. 首先尝试 NetMusic 原始验证（支持 m3u8 和央广网 API）
     * 2. 如果原始验证失败，使用放宽的验证逻辑
     */
    public static boolean isValidStreamUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            if (remoteIsValid != null) {
                Boolean result = (Boolean) remoteIsValid.invoke(null, url);
                if (result != null && result) {
                    return true;
                }
            }
        } catch (Throwable t) {
            NetMusicRadioAddon.LOGGER.debug("Remote validation failed, using relaxed validation", t);
        }

        return relaxedValidation(url);
    }

    /**
     * 放宽的 URL 验证逻辑
     */
    private static boolean relaxedValidation(String url) {
        try {
            URL u = URI.create(url.trim()).toURL();
            String protocol = u.getProtocol();

            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol) && !"file".equalsIgnoreCase(protocol)) {
                return false;
            }

            String path = u.getPath();
            if (path != null && !path.isEmpty()) {
                String lower = path.toLowerCase();
                if (hasAudioExtension(lower)) {
                    return true;
                }
            }

            if (isShoutcastStream(u)) {
                return true;
            }

            if (isStreamingService(u.getHost(), path)) {
                return true;
            }

            return path == null || path.isEmpty() || "/".equals(path);

        } catch (Exception e) {
            NetMusicRadioAddon.LOGGER.debug("URL parsing failed for: {}", url, e);
            return false;
        }
    }

    /**
     * 检测是否为 Shoutcast/Icecast 流
     * <p>
     * Shoutcast 特征：
     * - 常见端口: 8000, 8080, 8888, 9000, 10000
     * - 路径: 通常是一个数字 ID（如 /69366）
     * - 没有文件扩展名
     */
    public static boolean isShoutcastStream(URL url) {
        if (url == null) {
            return false;
        }

        int port = url.getPort();
        String portStr = port > 0 ? String.valueOf(port) : "";
        String path = url.getPath();

        if (!portStr.isEmpty() && SHOUTCAST_PORTS.contains(portStr)) {
            return true;
        }

        if (path != null && SHOUTCAST_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }

        if (path != null && !path.isEmpty() && STREAM_ID_PATTERN.matcher(path).matches()) {
            String lowerPath = path.toLowerCase();
            if (!hasAudioExtension(lowerPath) && !lowerPath.endsWith(".html") && !lowerPath.endsWith(".htm")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检测是否为常见流媒体服务
     */
    public static boolean isStreamingService(String host, String path) {
        if (host == null) {
            return false;
        }

        String lowerHost = host.toLowerCase();
        for (String domain : STREAMING_SERVICE_DOMAINS) {
            if (lowerHost.contains(domain)) {
                if (path == null || path.isEmpty() || "/".equals(path)) {
                    return true;
                }
                if (path.length() > 1 && !hasAudioExtension(path.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查是否包含音频扩展名
     */
    public static boolean hasAudioExtension(String lowerPath) {
        String[] extensions = {
                ".m3u8", ".m3u", ".pls", ".mp3", ".aac", ".ogg", ".wav",
                ".flac", ".m4a", ".opus", ".wma", ".webm", ".amr", ".flv", ".ts"
        };
        for (String ext : extensions) {
            if (lowerPath.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}