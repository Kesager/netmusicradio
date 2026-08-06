package com.github.tartaricacid.netmusicradio.client.api;

import com.github.tartaricacid.netmusic.api.NetEaseMusic;
import com.github.tartaricacid.netmusic.api.NetWorker;
import com.github.tartaricacid.netmusic.client.api.IAudioStreamHandler;
import com.github.tartaricacid.netmusic.client.audio.MusicBufferedInputStream;
import com.github.tartaricacid.netmusic.util.Mp3Util;
import com.github.tartaricacid.netmusicradio.client.util.BigMegaphoneUtilProxy;
import com.google.common.net.HttpHeaders;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * 处理 Shoutcast/Icecast 流媒体的音频处理器
 * <p>
 * 关键设计：
 * 1. 不发送 ICY: 1 头，让服务器返回纯净的音频流（无元数据嵌入）
 * 2. 使用 BufferedInputStream 包装原始流，以支持 mark/reset
 * 3. 使用 MusicBufferedInputStream 防止 read 方法的 IOException 导致死循环
 * <p>
 * 关于 Shoutcast 协议：
 * - 当客户端不发送 ICY: 1 头时，服务器返回纯音频数据
 * - 只有发送 ICY: 1 时，服务器才会在音频流中插入元数据块
 * - 大多数 Shoutcast 服务器都支持无元数据模式
 */
public class ShoutcastStreamHandler implements IAudioStreamHandler {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    @Override
    public boolean canHandle(URL url) {
        if (url == null) {
            return false;
        }
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            return false;
        }

        String path = url.getPath();
        if (path != null && hasAudioExtension(path.toLowerCase())) {
            return false;
        }

        return BigMegaphoneUtilProxy.isShoutcastStream(url)
                || BigMegaphoneUtilProxy.isStreamingService(url.getHost(), path);
    }

    @Override
    public AudioInputStream handle(URL url) throws UnsupportedAudioFileException, IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(CONNECT_TIMEOUT)
                .header(HttpHeaders.USER_AGENT, NetEaseMusic.getUserAgent())
                .GET()
                .build();

        HttpResponse<InputStream> response = NetWorker.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        int statusCode = response.statusCode();
        if (statusCode != 200 && statusCode != 201) {
            throw new IOException("Failed to connect to stream server. HTTP Status: " + statusCode);
        }

        InputStream rawStream = Optional.ofNullable(response.body())
                .orElseThrow(() -> new IOException("Empty response body from stream server"));

        BufferedInputStream bufferedStream = new MusicBufferedInputStream(rawStream);
        Mp3Util.skipID3(bufferedStream);
        return AudioSystem.getAudioInputStream(bufferedStream);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    private boolean hasAudioExtension(String lowerPath) {
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