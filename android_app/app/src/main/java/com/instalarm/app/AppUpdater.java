package com.instalarm.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppUpdater {

    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/Vamp-Niklaus/InstaClock/releases/latest";
    private static final String LATEST_RELEASE_WEB = "https://github.com/Vamp-Niklaus/InstaClock/releases/latest";

    public static String getCurrentVersion(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0.0";
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    public static void checkForUpdate(Activity activity, boolean isManualCheck, SharedPreferences prefs) {
        if (activity == null || activity.isFinishing()) return;

        String currentVersion = getCurrentVersion(activity);
        if (isManualCheck) {
            Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            String latestTag = null;
            String apkUrl = null;

            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "InstaClock-Android");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    latestTag = json.optString("tag_name", "");
                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}

            // Fallback: follow /releases/latest redirect to /releases/tag/vX.Y.Z
            if (latestTag == null || latestTag.isEmpty()) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_RELEASE_WEB).openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    int code = conn.getResponseCode();
                    if (code == 301 || code == 302) {
                        String location = conn.getHeaderField("Location");
                        if (location != null && location.contains("/tag/")) {
                            latestTag = location.substring(location.lastIndexOf("/tag/") + 5);
                        }
                    }
                    conn.disconnect();
                } catch (Exception ignored) {}
            }

            if ((apkUrl == null || apkUrl.isEmpty()) && latestTag != null && !latestTag.isEmpty()) {
                String clean = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
                apkUrl = "https://github.com/Vamp-Niklaus/InstaClock/releases/download/" + latestTag + "/InstaClock-v" + clean + ".apk";
            }

            final String finalTag = latestTag != null ? latestTag.trim() : "";
            final String finalUrl = apkUrl;

            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                if (finalTag.isEmpty()) {
                    if (isManualCheck) {
                        Toast.makeText(activity, "Could not check for updates", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                if (isNewerVersion(finalTag, currentVersion)) {
                    if (!isManualCheck) {
                        String alreadyPrompted = prefs.getString("prompted_update_version", "");
                        if (finalTag.equals(alreadyPrompted)) {
                            return; // Already asked once for this version
                        }
                        prefs.edit().putString("prompted_update_version", finalTag).apply();
                    }

                    new AlertDialog.Builder(activity)
                            .setTitle("Update Available (" + finalTag + ")")
                            .setMessage("A new version of InstaClock is available.\nCurrent: v" + currentVersion + "\nLatest: " + finalTag)
                            .setPositiveButton("Update Now", (d, w) -> downloadAndInstallApk(activity, finalUrl))
                            .setNegativeButton("Later", null)
                            .show();
                } else if (isManualCheck) {
                    Toast.makeText(activity, "You're on the latest version (v" + currentVersion + ")", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private static boolean isNewerVersion(String remoteTag, String localVersion) {
        String r = remoteTag.replaceFirst("^v", "").split("-")[0];
        String l = localVersion.replaceFirst("^v", "").split("-")[0];
        String[] rParts = r.split("\\.");
        String[] lParts = l.split("\\.");
        int len = Math.max(rParts.length, lParts.length);
        for (int i = 0; i < len; i++) {
            int rNum = i < rParts.length ? parseIntSafe(rParts[i]) : 0;
            int lNum = i < lParts.length ? parseIntSafe(lParts[i]) : 0;
            if (rNum > lNum) return true;
            if (rNum < lNum) return false;
        }
        return false;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public static void downloadAndInstallApk(Activity activity, String apkUrl) {
        if (apkUrl == null || apkUrl.isEmpty()) {
            Toast.makeText(activity, "Download link not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Allow App Installs")
                    .setMessage("Enable 'Allow from this source' so InstaClock can install updates directly.")
                    .setPositiveButton("Settings", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(60, 40, 60, 40);

        TextView tvProgress = new TextView(activity);
        tvProgress.setText("Downloading update... 0%");
        tvProgress.setTextSize(14f);
        container.addView(tvProgress);

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setPadding(0, 24, 0, 0);
        container.addView(progressBar);

        AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setTitle("Updating InstaClock")
                .setView(container)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                File updatesDir = new File(activity.getCacheDir(), "updates");
                if (!updatesDir.exists()) updatesDir.mkdirs();
                File apkFile = new File(updatesDir, "InstaClock-update.apk");

                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();

                int fileLength = conn.getContentLength();
                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    output.write(buffer, 0, count);
                    if (fileLength > 0) {
                        int pct = (int) ((total * 100) / fileLength);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            progressBar.setProgress(pct);
                            tvProgress.setText("Downloading update... " + pct + "%");
                        });
                    }
                }
                output.flush();
                output.close();
                input.close();
                conn.disconnect();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Uri apkUri = FileProvider.getUriForFile(activity,
                            activity.getPackageName() + ".fileprovider", apkFile);
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(installIntent);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
