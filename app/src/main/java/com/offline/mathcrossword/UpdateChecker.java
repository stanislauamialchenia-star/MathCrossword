package com.offline.mathcrossword;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateChecker {
    interface Callback {
        void onResult(String latestVersion, String downloadUrl, boolean newer);
        void onError(String message);
    }

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/stanislauamialchenia-star/MathCrossword/releases/latest";

    private UpdateChecker() {}

    static void check(String installedVersion, Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "MathCrossword-Android");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("GitHub HTTP " + code);

                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }

                String json = body.toString();
                Matcher tag = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"v?([^\\\"]+)\\\"").matcher(json);
                if (!tag.find()) throw new IllegalStateException("Не удалось прочитать версию релиза");
                String latestRaw = tag.group(1);
                Matcher version = Pattern.compile("(\\d+(?:\\.\\d+)+)").matcher(latestRaw);
                if (!version.find()) throw new IllegalStateException("Не удалось распознать версию релиза");
                String latestVersion = version.group(1);

                String downloadUrl = null;
                Matcher asset = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.apk)\\\"").matcher(json);
                if (asset.find()) downloadUrl = asset.group(1).replace("\\/", "/");

                callback.onResult(latestVersion, downloadUrl, compareVersions(latestVersion, installedVersion) > 0);
            } catch (Exception ex) {
                callback.onError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "mathcrossword-update-check").start();
    }

    static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int av = i < a.length ? parsePart(a[i]) : 0;
            int bv = i < b.length ? parsePart(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int parsePart(String part) {
        try { return Integer.parseInt(part.replaceAll("[^0-9].*$", "")); }
        catch (RuntimeException ignored) { return 0; }
    }
}
