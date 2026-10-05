package com.hyper.icecream.hook;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Hyper Ice Cream 内置模块（全合一）。
 *  1 传送门修复  2 剪贴板放行  3 旧组件恢复（miuiWidget 注入）
 *  4 组件尺寸重写（对齐官方格数→可堆叠，HyperStar 同思路）
 *  5 Dock 三常驻图标隐藏/透明（配置开关）
 *  6 假信号实验（订阅信息注入，配置开关，默认关）
 *  配置: /data/system/hypericecream_widget_whitelist.txt
 *        /data/system/hypericecream_widget_sizes.txt
 *        /data/system/hypericecream_dock.conf
 *        /data/system/hypericecream_signal.conf */
public class HyperHook implements IXposedHookLoadPackage {
    private static final String TAG = "HyperIceCream";
    private static final String CATCHER = "com.miui.contentcatcher";
    private static final String EXT = "com.miui.contentextension";
    private static final String MIUI_WIDGET_KEY = "miuiWidget";
    private static final String APPWIDGET_PROVIDER_KEY = "android.appwidget.provider";
    private static final String WL_FILE = "/data/system/hypericecream_widget_whitelist.txt";
    private static final String SIZE_FILE = "/data/system/hypericecream_widget_sizes.txt";
    private static final String DOCK_FILE = "/data/system/hypericecream_dock.conf";
    private static final String SIG_FILE = "/data/system/hypericecream_signal.conf";

    private static volatile Set<String> targets = new HashSet<String>();
    private static volatile java.util.HashMap<String, int[]> sizes =
            new java.util.HashMap<String, int[]>();
    private static volatile java.util.HashMap<String, String> dockConf =
            new java.util.HashMap<String, String>();
    private static volatile java.util.HashMap<String, String> sigConf =
            new java.util.HashMap<String, String>();
    private static volatile long lastCheck = 0;
    private static final Set<String> loggedOnce = new HashSet<String>();
    private static final java.util.IdentityHashMap<Object, Object> stash =
            new java.util.IdentityHashMap<Object, Object>();

    private static void logOnce(String k, String m) {
        synchronized (loggedOnce) {
            if (loggedOnce.add(k)) XposedBridge.log(TAG + ": " + m);
        }
    }

    private static synchronized void reloadConfig() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastCheck < 3000) return;
        lastCheck = now;
        Set<String> wl = new HashSet<String>();
        java.util.HashMap<String, int[]> sz = new java.util.HashMap<String, int[]>();
        java.util.HashMap<String, String> dk = new java.util.HashMap<String, String>();
        java.util.HashMap<String, String> sg = new java.util.HashMap<String, String>();
        boolean en = true;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(WL_FILE));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("enabled=")) en = "1".equals(line.substring(8));
                else if (line.matches("[A-Za-z0-9_.]+")) wl.add(line);
            }
            br.close();
        } catch (Throwable ignored) {
        }
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(SIZE_FILE));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                if (p.length == 3) sz.put(p[0], new int[]{
                        Integer.parseInt(p[1]), Integer.parseInt(p[2])});
            }
            br.close();
        } catch (Throwable ignored) {
        }
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(DOCK_FILE));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                int i = line.indexOf('=');
                dk.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
            }
            br.close();
        } catch (Throwable ignored) {
        }
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(SIG_FILE));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                int i = line.indexOf('=');
                sg.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
            }
            br.close();
        } catch (Throwable ignored) {
        }
        if (!en) wl.clear();
        targets = wl;
        sizes = sz;
        dockConf = dk;
        sigConf = sg;
    }

    private static String conf(String map, String key, String def) {
        java.util.HashMap<String, String> m =
                "dock".equals(map) ? dockConf : sigConf;
        String v = m.get(key);
        return v == null ? def : v;
    }

    private static boolean isTarget(String pkg) {
        if (pkg == null) return false;
        reloadConfig();
        return targets.contains(pkg);
    }

    // ===== 反射框架访问 =====
    private static Object getFieldVal(Object obj, String field) {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean setFieldVal(Object obj, String field, Object val) {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                f.set(obj, val);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Object call(Object obj, String method) {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(method);
                m.setAccessible(true);
                return m.invoke(obj);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static String pkgOf(Object ai) {
        Object v = getFieldVal(ai, "packageName");
        return v instanceof String ? (String) v : null;
    }

    private static String nameOf(Object ai) {
        Object v = getFieldVal(ai, "name");
        return v instanceof String ? (String) v : null;
    }

    private static boolean isWidgetReceiver(Object ai) {
        Object md = getFieldVal(ai, "metaData");
        if (md == null) return false;
        try {
            Object r = md.getClass().getMethod("containsKey", String.class)
                    .invoke(md, APPWIDGET_PROVIDER_KEY);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void injectMiuiWidget(Object ai) {
        Object md = getFieldVal(ai, "metaData");
        boolean already = false;
        try {
            if (md != null) {
                Object r = md.getClass().getMethod("getBoolean", String.class)
                        .invoke(md, MIUI_WIDGET_KEY);
                already = Boolean.TRUE.equals(r);
            }
        } catch (Throwable ignored) {
        }
        if (already) return;
        try {
            android.os.Bundle bundle;
            if (md == null) bundle = new android.os.Bundle();
            else bundle = new android.os.Bundle((android.os.Bundle) md);
            bundle.putBoolean(MIUI_WIDGET_KEY, true);
            if (setFieldVal(ai, "metaData", bundle)) {
                logOnce("inj:" + pkgOf(ai) + "/" + nameOf(ai),
                        "injected miuiWidget=true for " + pkgOf(ai) + "/" + nameOf(ai));
            }
        } catch (Throwable t) {
            logOnce("inj-err", "inject failed: " + t);
        }
    }

    private static void applySizeOverride(Object providerInfo, Object ai) {
        String pkg = pkgOf(ai);
        if (pkg == null) return;
        int[] wh = sizes.get(pkg);
        if (wh == null) return;
        int w = wh[0], h = wh[1], cell = 70;
        setIntIfPresent(providerInfo, "minWidth", w * cell);
        setIntIfPresent(providerInfo, "minHeight", h * cell);
        setIntIfPresent(providerInfo, "targetCellWidth", w);
        setIntIfPresent(providerInfo, "targetCellHeight", h);
        setIntIfPresent(providerInfo, "minResizeWidth", w);
        setIntIfPresent(providerInfo, "minResizeHeight", h);
        setIntIfPresent(providerInfo, "maxResizeWidth", Math.max(w, 5));
        setIntIfPresent(providerInfo, "maxResizeHeight", Math.max(h, 5));
        logOnce("size:" + pkg, "size override " + w + "x" + h + " for " + pkg);
    }

    private static boolean setIntIfPresent(Object obj, String field, int val) {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                f.setInt(obj, val);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void mark(Object result) {
        try {
            if (result == null) return;
            String cls = result.getClass().getName();
            if (cls.equals("android.content.pm.ActivityInfo")) {
                markActivity(result, false);
            } else if (cls.equals("android.content.pm.ResolveInfo")) {
                Object ai = getFieldVal(result, "activityInfo");
                if (ai != null) markActivity(ai, false);
            } else if (cls.equals("android.appwidget.AppWidgetProviderInfo")) {
                Object ai = call(result, "getActivityInfo");
                if (ai == null) ai = getFieldVal(result, "providerInfo");
                if (ai != null) markActivity(ai, true);
                applySizeOverride(result, ai);
            } else if (result instanceof java.util.List) {
                for (Object o : (java.util.List<?>) result) mark(o);
            } else if (cls.contains("ParceledListSlice")) {
                Object inner = call(result, "getList");
                if (inner instanceof java.util.List)
                    for (Object o : (java.util.List<?>) inner) mark(o);
            }
        } catch (Throwable t) {
            logOnce("mark-err", "mark failed: " + t);
        }
    }

    private static void markActivity(Object ai, boolean force) {
        if (ai == null) return;
        String pkg = pkgOf(ai);
        if (!isTarget(pkg)) return;
        if (!force && !isWidgetReceiver(ai)) return;
        injectMiuiWidget(ai);
    }

    private static void tempParcel(String className) {
        try {
            Class<?> cls = Class.forName(className);
            Class<?> bridge = XposedBridge.class;
            Method hookAll = bridge.getMethod("hookAllMethods",
                    Class.class, String.class, XC_MethodHook.class);
            hookAll.invoke(null, cls, "writeToParcel", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object obj = param.thisObject;
                        Object ai = obj;
                        if (obj.getClass().getName().contains("AppWidgetProviderInfo")) {
                            ai = call(obj, "getActivityInfo");
                        }
                        if (ai == null) return;
                        String pkg = pkgOf(ai);
                        if (!isTarget(pkg) || !isWidgetReceiver(ai)) return;
                        synchronized (stash) {
                            if (stash.containsKey(obj)) return;
                            Object orig = getFieldVal(ai, "metaData");
                            android.os.Bundle md;
                            if (orig == null) md = new android.os.Bundle();
                            else md = new android.os.Bundle((android.os.Bundle) orig);
                            md.putBoolean(MIUI_WIDGET_KEY, true);
                            setFieldVal(ai, "metaData", md);
                            stash.put(obj, orig);
                        }
                        if (obj.getClass().getName().contains("AppWidgetProviderInfo")) {
                            applySizeOverride(obj, ai);
                        }
                    } catch (Throwable t) {
                        logOnce("parcel-err", "parcel marker failed: " + t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object obj = param.thisObject;
                    Object ai = obj;
                    if (obj.getClass().getName().contains("AppWidgetProviderInfo")) {
                        ai = call(obj, "getActivityInfo");
                    }
                    synchronized (stash) {
                        Object orig = stash.remove(obj);
                        if (orig != null && ai != null) setFieldVal(ai, "metaData", orig);
                    }
                }
            });
            XposedBridge.log(TAG + ": temp parcel hook on " + className);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": parcel hook failed " + className + ": " + t);
        }
    }

    private static void hookResultMethods(ClassLoader cl, String cls, String... names) {
        Class<?> bridge = XposedBridge.class;
        try {
            Class<?> c = Class.forName(cls, false, cl);
            for (final String mname : names) {
                try {
                    Method hookAll = bridge.getMethod("hookAllMethods",
                            Class.class, String.class, XC_MethodHook.class);
                    hookAll.invoke(null, c, mname, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            mark(param.result);
                        }
                    });
                    logOnce("hook:" + cls + mname, "hooked " + cls + "." + mname);
                } catch (Throwable t1) {
                    try {
                        Method hm = bridge.getMethod("hookMethod",
                                java.lang.reflect.Member.class, XC_MethodHook.class);
                        for (Method m : c.getDeclaredMethods()) {
                            if (m.getName().equals(mname)) hm.invoke(null, m, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    mark(param.result);
                                }
                            });
                        }
                        logOnce("hookm:" + cls + mname, "hookMethod " + cls + "." + mname);
                    } catch (Throwable t2) {
                        logOnce("hookfail:" + cls + mname, "hook failed: " + t2);
                    }
                }
            }
        } catch (Throwable t) {
            logOnce("cls:" + cls, "class not found: " + cls);
        }
    }

    // ===== 功能5: Dock 三常驻图标（资源流替换） =====
    private static volatile boolean dockHooked = false;

    private static void dockTweaks(final ClassLoader cl) {
        if (dockHooked) return;
        dockHooked = true;
        try {
            Class<?> am = XposedHelpers.findClass("android.content.res.AssetManager", cl);
            Class<?> bridge = XposedBridge.class;
            Method hookAll = bridge.getMethod("hookAllMethods",
                    Class.class, String.class, XC_MethodHook.class);
            hookAll.invoke(null, am, "open", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 1
                                || !(param.args[0] instanceof String)) return;
                        String name = (String) param.args[0];
                        if (!name.contains("flutter_assets/assets/images/")) return;
                        String dockStyle = conf("dock", "dock_style", "none");
                        String hideXiaoai = "hide".equals(dockStyle) ? "1" : "0";
                        String hideSearch = "hide".equals(dockStyle) ? "1" : "0";
                        boolean hit = "1".equals(hideXiaoai) && (
                                name.contains("xiaoai") || name.contains("phone") || name.contains("search"));
                        if (hit) {
                            logOnce("dock:" + name, "dock icon hidden: " + name);
                            param.result = new ByteArrayInputStream(new byte[0]);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.log(TAG + ": dock tweaks hook installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": dock tweaks hook failed: " + t);
        }
    }

    // ===== 功能6: 假信号（实验，订阅信息注入；默认关，需写 signal.conf） =====
    private static volatile boolean sigHooked = false;

    private static void fakeSignal(ClassLoader cl, String pkg) {
        if (sigHooked) return;
        sigHooked = true;
        try {
            String fake = conf("signal", "fake", "0");
            if (!"1".equals(fake)) {
                XposedBridge.log(TAG + ": fake signal disabled by config");
                return;
            }
            final String slotS = conf("signal", "slot", "1");
            final String disp = conf("signal", "name", "中国移动");
            final String carrier = conf("signal", "carrier", "CMCC");
            final String num = conf("signal", "number", "10086");
            final int slot = Integer.parseInt(slotS);
            Class<?> sm = XposedHelpers.findClass("android.telephony.SubscriptionManager", cl);
            Class<?> bridge = XposedBridge.class;
            Method hookAll = bridge.getMethod("hookAllMethods",
                    Class.class, String.class, XC_MethodHook.class);
            String[] methods = {"getActiveSubscriptionInfoList",
                    "getCompleteActiveSubscriptionInfoList", "getAllSubscriptionInfoList"};
            for (final String mn : methods) {
                hookAll.invoke(null, sm, mn, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object list = param.result;
                            if (list == null) return;
                            java.util.List<?> l = (java.util.List<?>) list;
                            for (Object sub : l) {
                                Object si = getFieldVal(sub, "simSlotIndex");
                                if (si instanceof Integer && ((Integer) si) == slot) return;
                            }
                            Object fake = buildFakeSubscription(disp, carrier, num, slot);
                            if (fake == null) return;
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> lo = (java.util.List<Object>) list;
                            lo.add(fake);
                            logOnce("sig:" + mn, "fake subscription appended in " + mn);
                        } catch (Throwable t) {
                            logOnce("sig-err:" + mn, "fake signal failed: " + t);
                        }
                    }
                });
                XposedBridge.log(TAG + ": fake signal hook on " + pkg + " " + mn);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": fake signal hook failed: " + t);
        }
    }

    private static Object buildFakeSubscription(String disp, String carrier, String num, int slot) {
        try {
            Class<?> c = Class.forName("android.telephony.SubscriptionInfo");
            Constructor<?> best = null;
            for (Constructor<?> ctor : c.getConstructors()) {
                if (best == null || ctor.getParameterTypes().length > best.getParameterTypes().length) {
                    best = ctor;
                }
            }
            if (best == null) return null;
            Class<?>[] ps = best.getParameterTypes();
            Object[] args = new Object[ps.length];
            for (int i = 0; i < ps.length; i++) {
                Class<?> t = ps[i];
                if (t == int.class) args[i] = 10086 + i;
                else if (t == boolean.class) args[i] = Boolean.FALSE;
                else if (t == String.class) args[i] = i == 1 ? "898600000000000000" : (i == 7 ? num : "46000");
                else if (t == CharSequence.class) args[i] = i == 3 ? disp : (i == 4 ? carrier : disp);
                else args[i] = null;
            }
            // 基本槽位与着色
            for (int i = 0; i < ps.length; i++) {
                if (ps[i] == int.class && i <= 4) {
                    if (i == 2) args[i] = slot;
                }
            }
            return best.newInstance(args);
        } catch (Throwable t) {
            logOnce("sub-ctor", "SubscriptionInfo ctor failed: " + t);
            return null;
        }
    }

    public void handleLoadPackage(LoadPackageParam lpp) {
        if (CATCHER.equals(lpp.packageName)) {
            XposedBridge.log(TAG + ": in contentcatcher, targeting x.a.b gate");
            try {
                Class<?> xa = XposedHelpers.findClass("x.a", lpp.classLoader);
                XC_MethodHook hook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + ": neutered gate for " +
                                (param.args != null && param.args.length > 0 ? param.args[0] : "?"));
                        param.result = null;
                    }
                };
                try {
                    Method m = XposedBridge.class.getMethod("hookAllMethods",
                            Class.class, String.class, XC_MethodHook.class);
                    m.invoke(null, xa, "b", hook);
                    XposedBridge.log(TAG + ": hookAllMethods(x.a, b) OK");
                } catch (Throwable t) {
                    Member b = xa.getDeclaredMethod("b", String.class);
                    Method m = XposedBridge.class.getMethod("hookMethod",
                            Member.class, XC_MethodHook.class);
                    m.invoke(null, b, hook);
                    XposedBridge.log(TAG + ": hookMethod(x.a.b) OK");
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": catcher hook failed: " + t);
            }
            return;
        }

        if ("android".equals(lpp.packageName)) {
            XposedBridge.log(TAG + ": in system_server");
            try {
                Class<?> cs = XposedHelpers.findClass(
                        "com.android.server.clipboard.ClipboardService", lpp.classLoader);
                clipboardGate(cs, "clipboardAccessAllowed", true);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": ClipboardService hook failed: " + t);
            }
            try {
                Class<?> stub = XposedHelpers.findClass(
                        "com.android.server.clipboard.ClipboardServiceStub", lpp.classLoader);
                clipboardGate(stub, "clipboardAccessResult", false);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Stub hook failed: " + t);
            }
            tempParcel("android.app.ActivityInfo");
            tempParcel("android.appwidget.AppWidgetProviderInfo");
            fakeSignal(lpp.classLoader, "android");
            return;
        }

        if ("com.android.systemui".equals(lpp.packageName)) {
            XposedBridge.log(TAG + ": in systemui");
            fakeSignal(lpp.classLoader, "com.android.systemui");
            bootUnlock(lpp.classLoader);
            return;
        }

        if ("com.miui.home".equals(lpp.packageName)) {
            XposedBridge.log(TAG + ": in com.miui.home");
            hookResultMethods(lpp.classLoader, "android.app.ApplicationPackageManager",
                    "getReceiverInfo", "queryBroadcastReceivers", "queryBroadcastReceiversAsUser",
                    "resolveBroadcast");
            hookResultMethods(lpp.classLoader, "android.content.pm.IPackageManager$Stub$Proxy",
                    "getReceiverInfo", "queryIntentReceivers");
            hookResultMethods(lpp.classLoader, "android.content.pm.PackageParser",
                    "generateActivityInfo");
            hookResultMethods(lpp.classLoader, "android.content.pm.PackageInfoWithoutStateUtils",
                    "generateActivityInfo");
            hookResultMethods(lpp.classLoader, "android.appwidget.AppWidgetManager",
                    "getAppWidgetInfo", "getInstalledProviders",
                    "getInstalledProvidersForProfile", "getInstalledProvidersForPackage");
            hookResultMethods(lpp.classLoader, "com.android.internal.appwidget.IAppWidgetService$Stub$Proxy",
                    "getAppWidgetInfo", "getInstalledProvidersForProfile", "getInstalledProvidersForPackage");
            dockTweaks(lpp.classLoader);
        }
    }

    // ===== 7 开机自动解锁（实验）：SystemUI 进程，验证凭据后 keyguardDone =====
    private static final String UNLOCK_FILE = "/data/system/hypericecream_unlock.conf";
    private static volatile Object sKvm = null;

    private static java.util.HashMap<String, String> readKvFile(String path) {
        java.util.HashMap<String, String> m = new java.util.HashMap<String, String>();
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                int i = line.indexOf('=');
                m.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
            }
            br.close();
        } catch (Throwable ignored) {
        }
        return m;
    }

    private static String sysProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class).invoke(null, key);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 捕获 KeyguardViewMediator 实例（开机后 handleShow 必经，构造器兜底） */
    private static void captureMediator(ClassLoader cl) {
        if (sKvm != null) return;
        Class<?> kvm = null;
        try {
            kvm = XposedHelpers.findClass("com.android.systemui.keyguard.KeyguardViewMediator", cl);
        } catch (Throwable t1) {
            try {
                kvm = XposedHelpers.findClass("com.android.keyguard.KeyguardViewMediator", cl);
            } catch (Throwable t2) {
                XposedBridge.log(TAG + ": bootUnlock mediator class not found");
                return;
            }
        }
        final Class<?> fKvm = kvm;
        try {
            Method hac = XposedBridge.class.getMethod("hookAllConstructors",
                    Class.class, XC_MethodHook.class);
            hac.invoke(null, fKvm, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (sKvm == null && param.thisObject != null) {
                        sKvm = param.thisObject;
                        XposedBridge.log(TAG + ": bootUnlock mediator captured");
                    }
                }
            });
            XposedBridge.log(TAG + ": bootUnlock mediator hook armed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": bootUnlock capture failed: " + t);
        }
    }

    private static void unlockViaMediator() {
        Object m = sKvm;
        if (m == null) {
            XposedBridge.log(TAG + ": bootUnlock no mediator instance");
            return;
        }
        Class<?> c = m.getClass();
        // 调试：dump 全部方法名（一次性）
        synchronized (loggedOnce) {
            if (loggedOnce.add("kvmDump")) {
                StringBuilder sb = new StringBuilder("bootUnlock mediator methods:");
                for (Method mm : c.getDeclaredMethods()) {
                    sb.append(' ').append(mm.getName());
                }
                XposedBridge.log(TAG + ": " + sb);
            }
        }
        for (Method mm : c.getDeclaredMethods()) {
            if (!"keyguardDone".equals(mm.getName())) continue;
            Class<?>[] pt = mm.getParameterTypes();
            try {
                mm.setAccessible(true);
                if (pt.length == 1 && pt[0] == boolean.class) {
                    mm.invoke(m, Boolean.TRUE);
                    XposedBridge.log(TAG + ": bootUnlock keyguardDone(true) OK");
                    return;
                }
                if (pt.length == 2 && pt[0] == boolean.class && pt[1] == boolean.class) {
                    mm.invoke(m, Boolean.TRUE, Boolean.FALSE);
                    XposedBridge.log(TAG + ": bootUnlock keyguardDone(true,false) OK");
                    return;
                }
            } catch (Throwable ig) {
            }
        }
        try {
            Method hm = c.getDeclaredMethod("handleKeyguardDone");
            hm.setAccessible(true);
            hm.invoke(m);
            XposedBridge.log(TAG + ": bootUnlock handleKeyguardDone OK");
            return;
        } catch (Throwable ig) {
        }
        // HyperOS: post 到 mediator 自己的 Handler 线程，按默认值填充调用
        Object handler = null;
        for (Field f : c.getDeclaredFields()) {
            if (android.os.Handler.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    handler = f.get(m);
                    break;
                } catch (Throwable ig2) {
                }
            }
        }
        if (handler instanceof android.os.Handler) {
            final Object fm = m;
            final Class<?> fc = c;
            ((android.os.Handler) handler).post(new Runnable() {
                @Override
                public void run() {
                    for (String name : new String[]{"keyguardDone", "handleKeyguardDone",
                            "tryKeyguardDone"}) {
                        for (Method mm : fc.getDeclaredMethods()) {
                            if (!name.equals(mm.getName())) continue;
                            try {
                                mm.setAccessible(true);
                                Class<?>[] pt = mm.getParameterTypes();
                                Object[] args = new Object[pt.length];
                                for (int i = 0; i < pt.length; i++) {
                                    if (pt[i] == boolean.class) args[i] = Boolean.TRUE;
                                    else if (pt[i] == int.class) args[i] = 0;
                                    else if (pt[i] == long.class) args[i] = 0L;
                                    else args[i] = null;
                                }
                                mm.invoke(fm, args);
                                XposedBridge.log(TAG + ": bootUnlock " + name
                                        + "/" + pt.length + " OK");
                                return;
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": bootUnlock " + name + ": " + t);
                            }
                        }
                    }
                }
            });
            return;
        }
        // 兜底：setShowingLocked(false)（milinkhook 同款）
        for (Method mm : c.getDeclaredMethods()) {
            if (!"setShowingLocked".equals(mm.getName())) continue;
            Class<?>[] pt = mm.getParameterTypes();
            try {
                mm.setAccessible(true);
                if (pt.length == 1 && pt[0] == boolean.class) {
                    mm.invoke(m, Boolean.FALSE);
                    XposedBridge.log(TAG + ": bootUnlock setShowingLocked(false) OK");
                    return;
                }
            } catch (Throwable ig) {
            }
        }
        XposedBridge.log(TAG + ": bootUnlock no keyguardDone variant found");
    }

    private static void bootUnlock(final ClassLoader cl) {
        captureMediator(cl);
        final java.util.HashMap<String, String> cfg = readKvFile(UNLOCK_FILE);
        if (!"1".equals(cfg.get("enabled"))) {
            XposedBridge.log(TAG + ": bootUnlock disabled by config");
            return;
        }
        final String pin = cfg.get("pin");
        final String type = cfg.get("type") == null ? "password" : cfg.get("type");
        if (pin == null || pin.trim().isEmpty()) {
            XposedBridge.log(TAG + ": bootUnlock no pin in config");
            return;
        }
        XposedBridge.log(TAG + ": bootUnlock armed (type=" + type + ")");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 120; i++) {
                        if ("1".equals(sysProp("sys.boot_completed"))) break;
                        Thread.sleep(2000);
                    }
                    Thread.sleep(8000); // 等 keyguard 完全显示
                    Class<?> lpuCls = Class.forName("com.android.internal.widget.LockPatternUtils");
                    Class<?> credCls = Class.forName("com.android.internal.widget.LockscreenCredential");
                    Object at = Class.forName("android.app.ActivityThread")
                            .getMethod("currentActivityThread").invoke(null);
                    Object ctx = at.getClass().getMethod("getSystemContext").invoke(at);
                    Object lpu = lpuCls.getConstructor(android.content.Context.class).newInstance(ctx);
                    Object cred;
                    if ("pin".equals(type)) {
                        cred = credCls.getMethod("createPin", CharSequence.class)
                                .invoke(null, pin.trim());
                    } else {
                        cred = credCls.getMethod("createPassword", CharSequence.class)
                                .invoke(null, pin.trim());
                    }
                    Object km = ctx.getClass().getMethod("getSystemService", String.class)
                            .invoke(ctx, "keyguard");
                    for (int i = 0; i < 8; i++) {
                        try {
                            Boolean locked = (Boolean) km.getClass()
                                    .getMethod("isKeyguardLocked").invoke(km);
                            if (!Boolean.TRUE.equals(locked)) {
                                XposedBridge.log(TAG + ": bootUnlock keyguard already down");
                                return;
                            }
                            Object resp = lpuCls.getMethod("verifyCredential",
                                    credCls, int.class, int.class).invoke(lpu, cred, 0, 0);
                            // 0=VERIFY_OK, 1=RETRY, -1=ERROR；isOk() 反射失败时兜底
                            Boolean ok = Boolean.FALSE;
                            try {
                                ok = (Boolean) resp.getClass().getMethod("isOk").invoke(resp);
                            } catch (Throwable ig) {
                                XposedBridge.log(TAG + ": bootUnlock isOk() reflect fail: " + ig);
                            }
                            if (!Boolean.TRUE.equals(ok)) {
                                try {
                                    Object rc = resp.getClass().getMethod("getResponseCode").invoke(resp);
                                    if (rc instanceof Integer && ((Integer) rc) == 0) ok = Boolean.TRUE;
                                } catch (Throwable ig2) {
                                }
                            }
                            if (!Boolean.TRUE.equals(ok)) {
                                Object f = getFieldVal(resp, "mResponseCode");
                                if (f instanceof Integer && ((Integer) f) == 0) ok = Boolean.TRUE;
                            }
                            XposedBridge.log(TAG + ": bootUnlock verify#" + i
                                    + " ok=" + ok + " resp=" + resp);
                            if (Boolean.TRUE.equals(ok)) {
                                unlockViaMediator();
                                return;
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": bootUnlock try " + i + ": " + t);
                        }
                        Thread.sleep(10000);
                    }
                    XposedBridge.log(TAG + ": bootUnlock gave up after retries");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": bootUnlock fatal: " + t);
                }
            }
        }, "hic-bootunlock").start();
    }

    private static void clipboardGate(Class<?> cs, final String method, final boolean enforce) {
        final XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null) {
                    for (Object arg : param.args) {
                        if (EXT.equals(arg)) {
                            XposedBridge.log(TAG + ": ALLOWED " + method + " for " + EXT);
                            param.result = enforce ? Boolean.TRUE : null;
                            return;
                        }
                    }
                }
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!enforce && param.args != null) {
                    for (Object arg : param.args) {
                        if (EXT.equals(arg)) {
                            XposedBridge.log(TAG + ": OBSERVE " + method + " result=" + param.result);
                            return;
                        }
                    }
                }
            }
        };
        try {
            Method m = XposedBridge.class.getMethod("hookAllMethods",
                    Class.class, String.class, XC_MethodHook.class);
            m.invoke(null, cs, method, hook);
            XposedBridge.log(TAG + ": clipboard hook OK " + method);
        } catch (Throwable t1) {
            try {
                Method m = XposedBridge.class.getMethod("hookMethod",
                        Member.class, XC_MethodHook.class);
                for (Member mm : cs.getDeclaredMethods()) {
                    if (mm.getName().equals(method)) m.invoke(null, mm, hook);
                }
            } catch (Throwable t2) {
                XposedBridge.log(TAG + ": clipboard hook failed: " + t2);
            }
        }
    }
}
