#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MIUI/HyperOS 主题直装工具 (theme_tool.py)
=========================================
绕过主题商店，把任意 mtz 主题包（或已解包的主题目录）的组件直接部署到
/data/system/theme/（主题引擎运行时目录），支持备份与还原。

用法:
  python theme_tool.py inspect  <mtz或目录>            查看主题包里有哪些组件
  python theme_tool.py backup                          备份设备当前主题到本地
  python theme_tool.py deploy <mtz或目录> [--only 组件名,组件名]
  python theme_tool.py restore <备份目录>               还原备份
  python theme_tool.py find-mtz                        在平板存储里找 mtz 文件

可部署组件: com.android.systemui(状态栏/电池图标), icons(图标),
            wallpaper(壁纸), lock_wallpaper(锁屏壁纸)
依赖: python3, adb 在 PATH 中, 设备已连接且 adb 有 root(KSU 授权 Shell)
"""
import argparse
import os
import subprocess
import sys
import tempfile
import time
import zipfile

SERIAL = None
THEME_DIR = '/data/system/theme'
OWNER = 'system_theme:system_theme'
MODE_FILE = '755'
MODE_WALLPAPER = '600'
COMPONENTS = ['com.android.systemui', 'icons', 'wallpaper', 'lock_wallpaper']
TS = time.strftime('%Y%m%d_%H%M%S')


def sh(cmd, timeout=60):
    r = subprocess.run(cmd, capture_output=True, shell=True,
                       encoding='utf-8', errors='replace', timeout=timeout)
    return (r.stdout or '') + (r.stderr or '')


def adb(cmd, timeout=60):
    prefix = 'adb -s %s ' % SERIAL if SERIAL else 'adb '
    return sh(prefix + cmd, timeout)


def adb_su(cmd, timeout=120):
    prefix = 'adb -s %s ' % SERIAL if SERIAL else 'adb '
    return sh('%sshell su -c "%s"' % (prefix, cmd.replace('"', '\\"')), timeout)


def adb_script(body, tag='tt', timeout=180):
    tmp = tempfile.NamedTemporaryFile('w', suffix='.sh', delete=False,
                                      encoding='utf-8', newline='\n')
    tmp.write(body)
    tmp.close()
    device_path = '/data/local/tmp/%s_%s.sh' % (tag, TS)
    adb('push "%s" %s' % (tmp.name, device_path), 60)
    os.unlink(tmp.name)
    return adb_su('sh %s' % device_path, timeout)


def pick_device():
    global SERIAL
    out = sh('adb devices')
    lines = [l for l in out.splitlines() if '\tdevice' in l]
    if not lines:
        print('!! 没有已连接的 adb 设备，请先无线调试连接')
        sys.exit(1)
    serials = [l.split('\t')[0].strip() for l in lines]
    if SERIAL not in serials:
        SERIAL = serials[0]
    print('设备: %s' % SERIAL)
    r = adb_su('id')
    if 'uid=0' not in r:
        print('!! adb 无 root，请在 KernelSU 管理器给 Shell 授权')
        sys.exit(1)


def collect_components(source, workdir):
    comps = {}
    extracted = os.path.join(workdir, 'extracted')
    os.makedirs(extracted, exist_ok=True)

    if os.path.isdir(source):
        base = source
    else:
        if not zipfile.is_zipfile(source):
            print('!! %s 不是有效的 zip/mtz 文件' % source)
            sys.exit(1)
        with zipfile.ZipFile(source) as z:
            z.extractall(extracted)
        base = extracted

    # 单文件形态（已是容器 zip）
    for comp in ('com.android.systemui', 'icons'):
        single = os.path.join(base, comp)
        if os.path.isfile(single):
            comps[comp] = single

    # 目录形态 → 打包成容器 zip
    for comp in ('com.android.systemui', 'icons'):
        cdir = os.path.join(base, comp)
        if os.path.isdir(cdir) and comp not in comps:
            packed = os.path.join(workdir, comp + '.zip')
            with zipfile.ZipFile(packed, 'w', zipfile.ZIP_DEFLATED) as z:
                for root, dirs, files in os.walk(cdir):
                    for f in files:
                        full = os.path.join(root, f)
                        arc = os.path.relpath(full, cdir).replace('\\', '/')
                        z.write(full, arc)
            comps[comp] = packed

    # 壁纸 / 锁屏壁纸（宽松匹配）
    if 'wallpaper' not in comps:
        for root, dirs, files in os.walk(base):
            for f in files:
                low = f.lower()
                if low.startswith('wallpaper') and low.endswith(('.jpg', '.png')) \
                        and 'lock' not in low:
                    comps['wallpaper'] = os.path.join(root, f)
    if 'lock_wallpaper' not in comps:
        for root, dirs, files in os.walk(base):
            for f in files:
                low = f.lower()
                if 'lock' in low and 'wallpaper' in low and low.endswith(('.jpg', '.png')):
                    comps['lock_wallpaper'] = os.path.join(root, f)

    # 兼容 mtz 里 wallpaper/ 目录下的 lock 壁纸命名
    return comps


def describe(comps):
    print('发现组件:')
    for c in COMPONENTS:
        if c in comps:
            print('  [可部署] %-22s %s (%d bytes)' % (
                c, os.path.basename(comps[c]), os.path.getsize(comps[c])))
    missing = [c for c in COMPONENTS if c not in comps]
    if missing:
        print('  [包中无] %s' % ', '.join(missing))


def backup_device(local_dir):
    os.makedirs(local_dir, exist_ok=True)
    device_bk = '/data/local/tmp/ttheme_backup_%s' % TS
    body = ('mkdir -p %s\ncp -a %s/* %s/\nchmod -R 777 %s\n'
            'echo BACKUP-START\nls %s\necho BACKUP-OK\n'
            % (device_bk, THEME_DIR, device_bk, device_bk, device_bk))
    out = adb_script(body, 'backup')
    if 'BACKUP-OK' not in out:
        print('!! 第一次备份失败，重试一次...')
        time.sleep(3)
        out = adb_script(body, 'backup')
    if 'BACKUP-OK' not in out:
        print('!! 设备端备份失败:\n' + out)
        sys.exit(1)
    names = out.split('BACKUP-START')[1].split('BACKUP-OK')[0].split()
    print('设备端文件:', names)
    for name in names:
        r = adb('pull "%s/%s" "%s/%s"' % (device_bk, name, local_dir, name), 120)
        if 'error' in r.lower() or 'failed' in r.lower():
            print('!! 拉取失败，重试: ' + name)
            adb('pull "%s/%s" "%s/%s"' % (device_bk, name, local_dir, name), 120)
    print('已备份到: %s' % local_dir)
    print('设备端副本: %s' % device_bk)
    return device_bk, local_dir


def deploy_components(comps):
    body = ''
    for name, local in comps.items():
        remote = '/data/local/tmp/deploy_%s_%s' % (TS, name)
        adb('push "%s" "%s"' % (local, remote), 300)
        mode = MODE_WALLPAPER if name == 'wallpaper' else MODE_FILE
        body += ('cp %s %s/%s\nchown %s %s/%s\nchmod %s %s/%s\n'
                 % (remote, THEME_DIR, name, OWNER, THEME_DIR, name,
                    mode, THEME_DIR, name))
    body += 'echo DEPLOY-OK\nls -la %s/\nam crash com.android.systemui\n' % THEME_DIR
    out = adb_script(body, 'deploy')
    ok = 'DEPLOY-OK' in out
    print(out)
    if ok:
        print('部署完成，SystemUI 已重启。壁纸如未变化，重启设备一次即可。')
    return ok


def main():
    p = argparse.ArgumentParser(description='MIUI/HyperOS 主题直装工具')
    p.add_argument('action', choices=['inspect', 'backup', 'deploy', 'restore', 'find-mtz'])
    p.add_argument('source', nargs='?', help='mtz 文件 / 目录 / 备份目录')
    p.add_argument('--only', help='仅部署指定组件, 逗号分隔')
    p.add_argument('-s', '--serial', help='adb 设备序列号')
    args = p.parse_args()

    if args.serial:
        global SERIAL
        SERIAL = args.serial

    if args.action == 'find-mtz':
        pick_device()
        print(adb_su('find /sdcard -iname "*.mtz" 2>/dev/null | head -20'))
        return

    if args.action == 'backup':
        pick_device()
        backup_device(os.path.join(os.getcwd(), 'theme_backup_' + TS))
        return

    if args.action == 'restore':
        if not args.source or not os.path.isdir(args.source):
            print('!! 请指定备份目录')
            sys.exit(1)
        pick_device()
        body = ''
        for f in os.listdir(args.source):
            local = os.path.join(args.source, f)
            remote = '/data/local/tmp/restore_%s_%s' % (TS, f)
            adb('push "%s" "%s"' % (local, remote), 120)
            mode = MODE_WALLPAPER if f == 'wallpaper' else MODE_FILE
            body += ('cp %s %s/%s\nchown %s %s/%s\nchmod %s %s/%s\n'
                     % (remote, THEME_DIR, f, OWNER, THEME_DIR, f, mode, THEME_DIR, f))
        body += 'echo RESTORE-OK\nam crash com.android.systemui\n'
        out = adb_script(body, 'restore')
        print(out)
        if 'RESTORE-OK' in out:
            print('还原完成，SystemUI 已重启。')
        return

    if args.action in ('inspect', 'deploy'):
        if not args.source or not os.path.exists(args.source):
            print('!! 请指定 mtz 文件或目录')
            sys.exit(1)
        pick_device()
        workdir = tempfile.mkdtemp(prefix='theme_tool_')
        comps = collect_components(args.source, workdir)
        describe(comps)
        if args.action == 'inspect':
            print('(inspect 模式不做任何修改)')
            return
        if not comps:
            print('!! 没有可部署的组件')
            sys.exit(1)
        if args.only:
            want = [c.strip() for c in args.only.split(',') if c.strip() in comps]
            comps = {k: v for k, v in comps.items() if k in want}
            print('仅部署: %s' % ', '.join(comps.keys()))
            if not comps:
                return
        bk = os.path.join(os.getcwd(), 'theme_backup_' + TS)
        backup_device(bk)
        if deploy_components(comps):
            print('如需还原: python theme_tool.py restore "%s"' % bk)


if __name__ == '__main__':
    main()
