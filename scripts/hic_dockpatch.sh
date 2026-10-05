#!/data/adb/ksu/bin/busybox sh
# Hyper Ice Cream dock patch (daemon): 隐藏底栏"超级小爱"+"搜索"快捷方式
# patch: com.miui.home libapp.so DockFunctionIconAdapter.buildFunctionIcons
#   0x989ae4 tbnz w0,#4 -> b (skip createXiaoaiIconItem)
#   0x989b7c tbnz w0,#4 -> b (skip createSearchIconItem)
# 桌面每次重启补丁丢失，守护循环自动重打（对已打补丁的进程零操作）
ORIG="80042037"
PATCHED="24000014"
D1=$((0x139AE4))
D2=$((0x139B7C))

PID=$(pidof com.miui.home)
if [ -z "$PID" ]; then
  echo "home not running"
  exit 1
fi
echo "home pid=$PID"

do_patch() {
  PID=$1
  LINE=$(grep -m1 " 0216c000 .*base.apk" /proc/$PID/maps)
  if [ -z "$LINE" ]; then
    echo "exact offset miss, fallback list:"
    grep "base.apk" /proc/$PID/maps | grep " r-xp " | head -6
    return 1
  fi
  START=$((0x${LINE%%-*}))
  echo "map start=$START"
  ADDR1=$(( START + D1 ))
  ADDR2=$(( START + D2 ))
  echo "addr1=$ADDR1 addr2=$ADDR2"

  C1=$(dd if=/proc/$PID/mem bs=1 skip=$ADDR1 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
  C2=$(dd if=/proc/$PID/mem bs=1 skip=$ADDR2 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
  echo "cur1=$C1 cur2=$C2"

  printf '\x24\x00\x00\x14' > /data/local/tmp/hicpw.bin
  if [ "$C1" = "$ORIG" ]; then
    dd if=/data/local/tmp/hicpw.bin of=/proc/$PID/mem bs=1 seek=$ADDR1 conv=notrunc 2>/dev/null
    echo "patched xiaoai"
  elif [ "$C1" = "$PATCHED" ]; then
    echo "xiaoai already patched"
  else
    echo "xiaoai UNEXPECTED bytes"
  fi
  if [ "$C2" = "$ORIG" ]; then
    dd if=/data/local/tmp/hicpw.bin of=/proc/$PID/mem bs=1 seek=$ADDR2 conv=notrunc 2>/dev/null
    echo "patched search"
  elif [ "$C2" = "$PATCHED" ]; then
    echo "search already patched"
  else
    echo "search UNEXPECTED bytes"
  fi
  V1=$(dd if=/proc/$PID/mem bs=1 skip=$ADDR1 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
  V2=$(dd if=/proc/$PID/mem bs=1 skip=$ADDR2 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
  echo "verify1=$V1 verify2=$V2"
  if [ "$V1" = "$PATCHED" ] && [ "$V2" = "$PATCHED" ]; then
    echo BOTH-PATCHED
    return 0
  fi
  return 1
}

# 带参数 = 单次模式（手动跑）；无参数 = 守护模式（service.d 开机自启）
if [ -n "$1" ]; then
  do_patch "$PID"
  exit $?
fi

echo "daemon mode: watching home restarts"
while true; do
  PID=$(pidof com.miui.home)
  if [ -n "$PID" ]; then
    LINE=$(grep -m1 " 0216c000 .*base.apk" /proc/$PID/maps 2>/dev/null)
    if [ -n "$LINE" ]; then
      START=$((0x${LINE%%-*}))
      A1=$(( START + D1 ))
      C1=$(dd if=/proc/$PID/mem bs=1 skip=$A1 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
      if [ "$C1" = "$ORIG" ]; then
        echo "$(date) re-patching home pid=$PID"
        do_patch "$PID"
      fi
    fi
  fi
  sleep 5
done
