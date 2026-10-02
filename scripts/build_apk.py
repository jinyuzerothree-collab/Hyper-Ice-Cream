#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HyperOS-Theme-Installer APK 构建脚本

依赖（自行准备）:
  - JDK 17+（含 javac）
  - Android SDK: android.jar (API 34+) 与 aapt2.exe（build-tools 内）
  - r8.jar（r8 releases 或 build-tools 内自带，用于 D8 dex）
  - uber-apk-signer.jar（https://github.com/patrickfav/uber-apk-signer）

用法: python build_apk.py
产物: build/signed/ 下已签名 APK
"""
import os
import sys
import shutil
import zipfile
import subprocess

# ================== 配置：按本机环境修改 ==================
JAVA_BIN = r'path\to\jdk\bin\java.exe'
JAVAC = r'path\to\jdk\bin\javac.exe'
AAPT2 = r'path\to\aapt2.exe'
R8_JAR = r'path\to\r8.jar'
SIGNER = r'path\to\uber-apk-signer.jar'
ANDROID_JAR = r'path\to\android.jar'   # API 34 及以上
# =========================================================

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(REPO, 'app', 'src', 'main')
B = os.path.join(REPO, 'build')


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True, shell=True,
                       encoding='utf-8', errors='replace')
    out = ((r.stdout or '') + (r.stderr or '')).strip()
    if r.returncode != 0:
        print('FAILED:', out[-3000:])
        sys.exit(1)
    if out:
        print(out[-800:])


def main():
    for k, v in [('JAVA_BIN', JAVA_BIN), ('JAVAC', JAVAC), ('AAPT2', AAPT2),
                 ('R8_JAR', R8_JAR), ('SIGNER', SIGNER), ('ANDROID_JAR', ANDROID_JAR)]:
        if 'path\\to' in v:
            print('!! 请先在脚本顶部配置 ' + k)
            sys.exit(1)

    if os.path.exists(B):
        shutil.rmtree(B)
    os.makedirs(B)

    run('"%s" compile --dir "%s" -o "%s\\res.zip"'
        % (AAPT2, os.path.join(APP, 'res'), B))
    gen = os.path.join(B, 'gen')
    run('"%s" link -o "%s\\base.apk" -I "%s" --manifest "%s" "%s\\res.zip" --java "%s"'
        % (AAPT2, B, ANDROID_JAR, os.path.join(APP, 'AndroidManifest.xml'), B, gen))

    out_cls = os.path.join(B, 'classes')
    src_main = os.path.join(APP, 'java', 'com', 'zcode', 'themetool')
    run('"%s" --release 8 -cp "%s" -d "%s" "%s" "%s"'
        % (JAVAC, ANDROID_JAR, out_cls,
           os.path.join(src_main, 'MainActivity.java'),
           os.path.join(gen, 'com', 'zcode', 'themetool', 'R.java')))

    cls_files = []
    for root, dirs, files in os.walk(out_cls):
        for f in files:
            if f.endswith('.class'):
                cls_files.append(os.path.join(root, f))
    dex_out = os.path.join(B, 'dex')
    os.makedirs(dex_out, exist_ok=True)
    run('"%s" -cp "%s" com.android.tools.r8.D8 --release --min-api 26 --output "%s" %s'
        % (JAVA_BIN, R8_JAR, dex_out, ' '.join('"%s"' % c for c in cls_files)))

    out_apk = os.path.join(B, 'themetool_unsigned.apk')
    zsrc = zipfile.ZipFile(os.path.join(B, 'base.apk'))
    zout = zipfile.ZipFile(out_apk, 'w')
    for item in zsrc.infolist():
        zout.writestr(item, zsrc.read(item.filename))
    zout.writestr('classes.dex', open(os.path.join(dex_out, 'classes.dex'), 'rb').read())
    zout.close()

    run('"%s" -jar "%s" --apks "%s" --out "%s\\signed"'
        % (JAVA_BIN, SIGNER, out_apk, B))
    final = os.path.join(B, 'signed', 'themetool_unsigned-aligned-debugSigned.apk')
    print('FINAL:', final, os.path.getsize(final), 'bytes')


if __name__ == '__main__':
    main()
