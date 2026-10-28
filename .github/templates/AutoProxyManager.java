package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground connection recovery. All mutable state belongs to the UI thread. */
public final class AutoProxyManager {
    private static final String[] SOURCES = {
        "https://cdn.jsdelivr.net/gh/hookzof/socks5_list@master/tg/mtproto.json",
        "https://cdn.jsdelivr.net/gh/soroushmirzaei/telegram-proxies-collector@main/proxies/mtproto",
        "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt"
    };
    private static final ExecutorService FETCHER = Executors.newSingleThreadExecutor();
    private static final long RETRY_MS = 60000;
    private static final long SEARCH_TIMEOUT_MS = 45000;
    private static final int MAX_CANDIDATES = 30;
    private static boolean started;
    private static boolean running;
    private static boolean applying;
    private static long generation;
    private static long disconnectedSince;
    private static long nextAttempt;

    public static void start() {
        if (started) return;
        started = true;
        SharedConfig.loadProxyList();
        NotificationCenter.getGlobalInstance().addObserver((id, account, args) -> {
            if (applying) return;
            SharedPreferences prefs = preferences();
            boolean ours = prefs.getBoolean("proxy_enabled", false)
                && endpoint(prefs).equals(prefs.getString("kvant_auto_proxy_key", ""));
            prefs.edit().putBoolean("kvant_auto_proxy", ours).apply();
            generation++;
            running = false;
            disconnectedSince = 0;
        }, NotificationCenter.proxySettingsChanged);
        AndroidUtilities.runOnUIThread(TICK, 10000);
    }

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            AndroidUtilities.runOnUIThread(this, 10000);
            if (ApplicationLoader.mainInterfacePaused || !allowed()) {
                disconnectedSince = 0;
                return;
            }
            int account = UserConfig.selectedAccount;
            if (connected(account) || !ApplicationLoader.isNetworkOnline()) {
                disconnectedSince = 0;
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (disconnectedSince == 0) disconnectedSince = now;
            // Give the direct connection (or the current proxy) time to recover.
            if (!running && now - disconnectedSince >= 20000 && now >= nextAttempt) {
                search(account);
            }
        }
    };

    private static boolean connected(int account) {
        int state = ConnectionsManager.getInstance(account).getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnected
            || state == ConnectionsManager.ConnectionStateUpdating;
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }

    private static String key(SharedConfig.ProxyInfo p) {
        return p.address + ":" + p.port + ":" + p.secret;
    }

    private static String endpoint(SharedPreferences p) {
        return p.getString("proxy_ip", "") + ":" + p.getInt("proxy_port", 1080)
            + ":" + p.getString("proxy_secret", "");
    }

    private static boolean allowed() {
        SharedPreferences p = preferences();
        if (!p.getBoolean("kvant_auto_proxy", true)) return false;
        // A manually configured proxy always wins over automatic selection.
        if (p.getBoolean("proxy_enabled", false)) {
            return endpoint(p).equals(p.getString("kvant_auto_proxy_key", ""));
        }
        // Respect disabling an automatically selected proxy, including after restart.
        return TextUtils.isEmpty(p.getString("kvant_auto_proxy_key", ""));
    }

    private static void search(int account) {
        running = true;
        final long token = ++generation;
        final String failed = preferences().getBoolean("proxy_enabled", false) ? endpoint(preferences()) : "";
        final List<SharedConfig.ProxyInfo> saved = new ArrayList<>(SharedConfig.proxyList);
        AndroidUtilities.runOnUIThread(() -> finish(token), SEARCH_TIMEOUT_MS);
        // HTTP I/O uses a dedicated worker, never Telegram's shared global queue.
        FETCHER.execute(() -> {
            Map<String, SharedConfig.ProxyInfo> candidates = new LinkedHashMap<>();
            Collections.shuffle(saved);
            for (SharedConfig.ProxyInfo p : saved) {
                if (candidates.size() >= 10) break;
                if (!key(p).equals(failed)) candidates.put(key(p), p);
            }
            for (String source : SOURCES) {
                try {
                    String response = fetch(source);
                    int added = 0;
                    if (source.endsWith(".json")) {
                        JSONArray array = new JSONArray(response);
                        int start = (int) (Math.random() * array.length());
                        for (int i = 0; i < array.length() && added < 10; i++) {
                            JSONObject item = array.optJSONObject((start + i) % array.length());
                            if (item != null && add(candidates, item.optString("host", item.optString("server")),
                                    item.optInt("port", 443), item.optString("secret"), failed)) added++;
                        }
                    } else {
                        List<String> lines = new ArrayList<>();
                        Collections.addAll(lines, response.split("\n"));
                        Collections.shuffle(lines);
                        for (String line : lines) {
                            if (added >= 10) break;
                            try {
                                Uri uri = Uri.parse(line.trim());
                                boolean proxyLink = "tg".equals(uri.getScheme()) && "proxy".equals(uri.getHost());
                                proxyLink |= "https".equals(uri.getScheme()) && "t.me".equals(uri.getHost()) && "/proxy".equals(uri.getPath());
                                if (proxyLink && add(candidates, uri.getQueryParameter("server"),
                                        Integer.parseInt(uri.getQueryParameter("port")), uri.getQueryParameter("secret"), failed)) added++;
                            } catch (Exception ignored) {
                                // One malformed line must not discard the rest of a feed.
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Other feeds and saved proxies remain available when a source fails.
                }
            }
            List<SharedConfig.ProxyInfo> list = new ArrayList<>(candidates.values());
            Collections.shuffle(list);
            AndroidUtilities.runOnUIThread(() -> probe(account, token, list));
        });
    }

    private static boolean add(Map<String, SharedConfig.ProxyInfo> list, String host, int port, String secret, String failed) {
        if (TextUtils.isEmpty(host) || host.length() > 253 || host.matches(".*\\s.*")
                || port < 1 || port > 65535 || TextUtils.isEmpty(secret) || secret.length() > 512) return false;
        SharedConfig.ProxyInfo p = new SharedConfig.ProxyInfo(host, port, "", "", secret);
        String key = key(p);
        if (key.equals(failed) || list.containsKey(key)) return false;
        list.put(key, p);
        return true;
    }

    private static void probe(int account, long token, List<SharedConfig.ProxyInfo> list) {
        if (!valid(account, token)) { finish(token); return; }
        int count = Math.min(MAX_CANDIDATES, list.size());
        if (count == 0) { finish(token); return; }
        for (int i = 0; i < count; i++) {
            SharedConfig.ProxyInfo candidate = list.get(i);
            ConnectionsManager.getInstance(account).checkProxy(candidate.address, candidate.port,
                candidate.username, candidate.password, candidate.secret,
                time -> AndroidUtilities.runOnUIThread(() -> {
                    // Generation prevents late callbacks from replacing a later choice.
                    if (time >= 0 && valid(account, token)) apply(candidate, token);
                }));
        }
    }

    private static boolean valid(int account, long token) {
        return running && token == generation && account == UserConfig.selectedAccount
            && !ApplicationLoader.mainInterfacePaused && allowed() && !connected(account);
    }

    private static void finish(long token) {
        if (token != generation) return;
        generation++;
        running = false;
        nextAttempt = SystemClock.elapsedRealtime() + RETRY_MS;
    }

    private static void apply(SharedConfig.ProxyInfo candidate, long token) {
        SharedConfig.ProxyInfo chosen = SharedConfig.addProxy(candidate);
        SharedConfig.currentProxy = chosen;
        preferences().edit().putBoolean("proxy_enabled", true)
            .putBoolean("proxy_enabled_calls", false)
            .putBoolean("kvant_auto_proxy", true).putString("kvant_auto_proxy_key", key(chosen))
            .putString("proxy_ip", chosen.address).putInt("proxy_port", chosen.port)
            .putString("proxy_user", chosen.username).putString("proxy_pass", chosen.password)
            .putString("proxy_secret", chosen.secret).apply();
        ConnectionsManager.setProxySettings(true, chosen.address, chosen.port, chosen.username, chosen.password, chosen.secret);
        finish(token);
        disconnectedSince = 0;
        applying = true;
        try {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        } finally {
            applying = false;
        }
    }

    private static String fetch(String source) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        long deadline = SystemClock.elapsedRealtime() + 10000;
        try {
            if (connection.getResponseCode() != 200 || !"https".equals(connection.getURL().getProtocol())) {
                throw new java.io.IOException("Proxy feed unavailable");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder result = new StringBuilder();
                char[] buffer = new char[4096];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    if (result.length() + length > 262144 || SystemClock.elapsedRealtime() > deadline) {
                        throw new java.io.IOException("Proxy feed exceeds limits");
                    }
                    result.append(buffer, 0, length);
                }
                return result.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
}
