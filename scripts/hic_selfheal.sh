#!/data/adb/ksu/bin/busybox sh
# Hyper Ice Cream LSPosed 自愈：开机后检查桌面进程是否被注入，
# 失败（LSPosed 间歇注入）则自动补一次重启。仅自动执行一次（标志文件防循环）。
LOGDIR=/data/adb/lspd/log
FLAG=/data/local/tmp/.hic_selfheal_done
MAXWAIT=150   # 开机后最多等 150 秒让 LSPosed 完成注入

# 只在开机后 5 分钟内运行
BOOTAge=$(cat /proc/uptime | cut -d' ' -f1 | cut -d. -f1)
[ "$BOOTAge" -gt 600 ] && exit 0
[ -f "$FLAG" ] && exit 0
touch "$FLAG"

i=0
while [ $i -lt $MAXWAIT ]; do
  sleep 5
  i=$((i+5))
  BC=$(getprop sys.boot_completed 2>/dev/null)
  [ "$BC" = "1" ] || continue
  HP=$(pidof com.miui.home)
  [ -n "$HP" ] || continue
  # 最新 modules log 里找 home 的 HyperIceCream 注入痕迹
  L=$(ls -t $LOGDIR/modules_*.log 2>/dev/null | head -1)
  [ -z "$L" ] && continue
  if grep -q "HyperIceCream: in com.miui.home" "$L" 2>/dev/null; then
    echo "$(date) self-heal: home injected OK"
    exit 0
  fi
  # systemui 也在同批注入，若 systemui 有日志而 home 没有，更确定是半注入
  if grep -q "HyperIceCream: in systemui" "$L" 2>/dev/null; then
    echo "$(date) self-heal: half-injection detected (home missing), rebooting once"
    sleep 3
    reboot
    exit 0
  fi
  # 两者都没有：可能日志轮换中，继续等
done
echo "$(date) self-heal: wait window expired"
