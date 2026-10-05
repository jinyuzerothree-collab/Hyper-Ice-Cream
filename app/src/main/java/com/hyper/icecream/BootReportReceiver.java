package com.hyper.icecream;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;

/** 开发者选项：开机自动上报本机地址给 PC 监听器（PC 端自动 adb connect）。
 *  配置存储：community_pref/report_url（默认 http://192.168.3.70:39291/report）、boot_report 开关。 */
public class BootReportReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        boolean on = ctx.getSharedPreferences("community_pref", Context.MODE_PRIVATE)
                .getBoolean("boot_report", true);
        if (!on) return;
        final String url = ctx.getSharedPreferences("community_pref", Context.MODE_PRIVATE)
                .getString("report_url", "http://192.168.3.70:39291/report");
        final Object pr; // DSH android.jar 无 PendingResult 类型，反射拿 goAsync
        Object prTmp = null;
        try {
            prTmp = getClass().getMethod("goAsync").invoke(this);
        } catch (Throwable ignored) {
        }
        pr = prTmp;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 等 boot 完成 + Wi-Fi 就绪
                    for (int i = 0; i < 90; i++) {
                        if ("1".equals(sysProp("sys.boot_completed"))) break;
                        Thread.sleep(2000);
                    }
                    Thread.sleep(12000);
                    String ip = null;
                    for (int i = 0; i < 20 && ip == null; i++) {
                        ip = wlanIp();
                        if (ip == null) Thread.sleep(3000);
                    }
                    if (ip == null) ip = "unknown";
                    // 双保险：确保 adbd 固定 5555（service.d 已有，这里兜底）
                    execSu("setprop service.adb.tcp.port 5555; stop adbd; start adbd");
                    Thread.sleep(3000);
                    String body = "{\"ip\":\"" + ip + "\",\"port\":\"5555\",\"model\":\""
                            + android.os.Build.MODEL.replace("\"", "") + "\"}";
                    for (int i = 0; i < 6; i++) {
                        if (post(url, body)) break;
                        Thread.sleep(10000);
                    }
                } catch (Throwable ignored) {
                } finally {
                    try {
                        if (pr != null) {
                            pr.getClass().getMethod("finish").invoke(pr);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "hic-bootreport").start();
    }

    private static String sysProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class).invoke(null, key);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String wlanIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                String name = ni.getName() == null ? "" : ni.getName();
                if (!ni.isUp() || ni.isLoopback()) continue;
                if (!name.startsWith("wlan") && !name.startsWith("eth")) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.isSiteLocalAddress() && a.getHostAddress().indexOf(':') < 0) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void execSu(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
        } catch (Throwable ignored) {
        }
    }

    private static boolean post(String urlStr, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            return code == 200;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
