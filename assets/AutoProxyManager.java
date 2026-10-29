package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoProxyManager {
    private static final String[] JSON_SOURCES = new String[]{
        "https://cdn.jsdelivr.net/gh/hookzof/socks5_list@master/tg/mtproto.json"
    };
    private static final String[] TXT_SOURCES = new String[]{
        "https://cdn.jsdelivr.net/gh/soroushmirzaei/telegram-proxies-collector@main/proxies/mtproto",
        "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt"
    };

    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final AtomicBoolean proxyApplied = new AtomicBoolean(false);

    public static void start() {
        if (isRunning.getAndSet(true)) return;
        Utilities.globalQueue.postRunnable(() -> {
            List<SharedConfig.ProxyInfo> list = new ArrayList<>();
            for (String src : JSON_SOURCES) {
                try {
                    String res = fetchUrl(src);
                    if (!TextUtils.isEmpty(res)) {
                        JSONArray arr = new JSONArray(res);
                        for (int i = 0; i < arr.length() && list.size() < 25; i++) {
                            JSONObject o = arr.getJSONObject(i);
                            String host = o.optString("host");
                            int port = o.optInt("port", 443);
                            String secret = o.optString("secret");
                            if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(secret)) {
                                list.add(new SharedConfig.ProxyInfo(host, port, "", "", secret));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            for (String src : TXT_SOURCES) {
                if (list.size() >= 30) break;
                try {
                    String res = fetchUrl(src);
                    if (!TextUtils.isEmpty(res)) {
                        for (String line : res.split("\n")) {
                            if (list.size() >= 30) break;
                            line = line.trim();
                            if (line.startsWith("tg://proxy") || line.startsWith("https://t.me/proxy")) {
                                Uri u = Uri.parse(line);
                                String s = u.getQueryParameter("server");
                                String p = u.getQueryParameter("port");
                                String sec = u.getQueryParameter("secret");
                                if (!TextUtils.isEmpty(s) && !TextUtils.isEmpty(p) && !TextUtils.isEmpty(sec)) {
                                    list.add(new SharedConfig.ProxyInfo(s, Integer.parseInt(p), "", "", sec));
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            proxyApplied.set(false);
            for (SharedConfig.ProxyInfo p : list) {
                ConnectionsManager.getInstance(0).checkProxy(p.address, p.port, "", "", p.secret, time -> {
                    if (time > 0 && time < 1500 && !proxyApplied.getAndSet(true)) {
                        AndroidUtilities.runOnUIThread(() -> {
                            SharedConfig.addProxy(p);
                            SharedConfig.currentProxy = p;
                            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            prefs.edit()
                                .putBoolean("proxy_enabled", true)
                                .putString("proxy_ip", p.address)
                                .putInt("proxy_port", p.port)
                                .putString("proxy_user", "")
                                .putString("proxy_pass", "")
                                .putString("proxy_secret", p.secret)
                                .apply();
                            ConnectionsManager.setProxySettings(true, p.address, p.port, "", "", p.secret);
                        });
                    }
                });
            }
            isRunning.set(false);
        });
    }

    private static String fetchUrl(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        if (c.getResponseCode() != 200) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l).append("\n");
        r.close();
        return sb.toString();
    }
}
