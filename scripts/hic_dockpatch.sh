#!/data/adb/ksu/bin/busybox sh
# Hyper Ice Cream dock patch v2 (daemon): 三图标独立开关
# 隐藏底栏常驻快捷方式，读配置 /data/system/hypericecream_dock.conf:
#   hide_xiaoai=1  patch buildFunctionIcons 里 createXiaoaiIconItem 闸门 (tbnz->b)
#   hide_search=1  patch buildFunctionIcons 里 createSearchIconItem 闸门 (tbnz->b)
#   hide_remote=1  patch MirrorIconManager._updateMirrorIcon 函数头 ret (互联/远控图标)
# 桌面每次重启补丁丢失，守护自动重打；开关关闭时自动恢复原字节。
X_REAL=$((0x191C000))      # libapp.so 数据在 base.apk 内的真实偏移(zipalign后)
TXTOFF=$((0x850000))       # .text LOAD 段 vaddr==fileoff
MAP_OFF=$((0x0216c000))    # maps 里 r-xp base.apk 的 offset 字面量
ORIG_GATE="80042037"
NEW_GATE="24000014"
ORIG_MIRROR="fd79bfa9"
RET_INSN="c0035fd6"
V_XIAOAI=$((0x989ae4))
V_SEARCH=$((0x989b7c))
V_REMOTE=$((0x16159f8))
V_BIGENT=$((0x1acb9f4))    # BigIconUtil.showBigIconEntrance -> 永远 true（解锁大图标入口）
ORIG_BIG="fd79bfa9"
BIG_TRUE="c0820091c0035fd6"  # add x0,x22,#0x20 (true) ; ret

CONF=/data/system/hypericecream_dock.conf
HX=1; HS=1; HR=1
if [ -f "$CONF" ]; then
  grep -q "hide_xiaoai=0" "$CONF" && HX=0
  grep -q "hide_search=0" "$CONF" && HS=0
  grep -q "hide_remote=0" "$CONF" && HR=0
fi

do_patch() {
  PID=$1
  LINE=$(grep -m1 " 0216c000 .*base.apk" /proc/$PID/maps)
  if [ -z "$LINE" ]; then return 1; fi
  START=$((0x${LINE%%-*}))
  BASE=$(( START - MAP_OFF + X_REAL ))
  RD() { dd if=/proc/$PID/mem bs=1 skip=$(($1)) count=4 2>/dev/null | od -An -tx1 | tr -d ' \n'; }
  WR() { printf "$2" > /data/local/tmp/hicpw.bin; dd if=/data/local/tmp/hicpw.bin of=/proc/$PID/mem bs=1 seek=$(($1)) conv=notrunc 2>/dev/null; }

  AX=$(( BASE + V_XIAOAI ))
  AS=$(( BASE + V_SEARCH ))
  AR=$(( BASE + V_REMOTE ))

  # 小爱
  C=$(RD $AX)
  if [ "$HX" = "1" ] && [ "$C" = "$ORIG_GATE" ]; then WR $AX '\x24\x00\x00\x14'; echo "patched xiaoai"; fi
  if [ "$HX" = "0" ] && [ "$C" = "$NEW_GATE" ]; then WR $AX '\x80\x04\x20\x37'; echo "restored xiaoai"; fi
  # 搜索
  C=$(RD $AS)
  if [ "$HS" = "1" ] && [ "$C" = "$ORIG_GATE" ]; then WR $AS '\x24\x00\x00\x14'; echo "patched search"; fi
  if [ "$HS" = "0" ] && [ "$C" = "$NEW_GATE" ]; then WR $AS '\x80\x04\x20\x37'; echo "restored search"; fi
  # 远控/互联（函数头 ret；恢复写回原序言）
  C=$(RD $AR)
  if [ "$HR" = "1" ] && [ "$C" = "$ORIG_MIRROR" ]; then WR $AR '\xc0\x03\x5f\xd6'; echo "patched remote"; fi
  if [ "$HR" = "0" ] && [ "$C" = "$RET_INSN" ]; then WR $AR '\xfd\x79\xbf\xa9'; echo "restored remote"; fi
  # 大图标入口解锁（showBigIconEntrance -> true，8 字节桩；恢复写回原序言）
  AB=$(( BASE + V_BIGENT ))
  CB=$(RD $AB)
  if [ "$CB" = "$ORIG_BIG" ]; then
    printf '\xc0\x82\x00\x91\xc0\x03\x5f\xd6' > /data/local/tmp/hicpw.bin
    dd if=/data/local/tmp/hicpw.bin of=/proc/$PID/mem bs=1 seek=$AB count=8 conv=notrunc 2>/dev/null
    echo "patched bigicon-entrance"
  elif [ "$CB" = "c0820091" ]; then
    echo "bigicon-entrance already patched"
  fi

  V1=$(RD $AX); V2=$(RD $AS); V3=$(RD $AR); V4=$(RD $AB)
  echo "state xiaoai=$V1 search=$V2 remote=$V3 bigent=$V4 conf=($HX,$HS,$HR)"
}

# 单次模式（带参数）
if [ -n "$1" ]; then
  PID=$(pidof com.miui.home)
  if [ -z "$PID" ]; then echo "home not running"; exit 1; fi
  do_patch "$PID"
  exit $?
fi

echo "hic dock patch daemon v2"
while true; do
  PID=$(pidof com.miui.home)
  if [ -n "$PID" ]; then
    LINE=$(grep -m1 " 0216c000 .*base.apk" /proc/$PID/maps 2>/dev/null)
    if [ -n "$LINE" ]; then
      START=$((0x${LINE%%-*}))
      AX=$(( START - MAP_OFF + X_REAL + V_XIAOAI ))
      C1=$(dd if=/proc/$PID/mem bs=1 skip=$AX count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
      AR=$(( START - MAP_OFF + X_REAL + V_REMOTE ))
      C3=$(dd if=/proc/$PID/mem bs=1 skip=$AR count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
      NEED=0
      if { [ "$HX" = "1" ] && [ "$C1" = "$ORIG_GATE" ]; } || { [ "$HX" = "0" ] && [ "$C1" = "$NEW_GATE" ]; }; then NEED=1; fi
      if { [ "$HR" = "1" ] && [ "$C3" = "$ORIG_MIRROR" ]; } || { [ "$HR" = "0" ] && [ "$C3" = "$RET_INSN" ]; }; then NEED=1; fi
      if [ "$NEED" = "1" ]; then
        echo "$(date) re-patching pid=$PID"
        do_patch "$PID"
      fi
    fi
  fi
  sleep 5
done
