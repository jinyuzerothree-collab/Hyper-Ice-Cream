#!/system/bin/sh
# 通用 hometweaks bin 功能注入器
# 用法: hictools_inject.sh <功能号...>   例: hictools_inject.sh 12 13
# 结构: "HTWK" | ver(u32) | enabled(u32) | count(u32) | ids[count](u32) | values...
BIN=/data/local/tmp/hometweaks.bin
BAK=/data/local/tmp/hometweaks.bin.bak

if [ ! -f "$BIN" ]; then echo "NO-BIN"; exit 1; fi
if [ -z "$1" ]; then echo "usage: $0 <feature-id...>"; exit 1; fi

cp "$BIN" "$BAK"
SIZE=$(wc -c < "$BIN")
CNT_HEX=$(dd if="$BIN" bs=1 skip=12 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
CNT=$((16#${CNT_HEX:6:2}${CNT_HEX:4:2}${CNT_HEX:2:2}${CNT_HEX:0:2}))
IDS_HEX=$(dd if="$BIN" bs=1 skip=16 count=$((CNT*4)) 2>/dev/null | od -An -tx1 | tr -d ' \n')
echo "old: size=$SIZE count=$CNT ids=$IDS_HEX"

# 追加不存在的新功能号
NEWCNT=$CNT
ADD=""
for NEWID in "$@"; do
  NH=$(printf '%08x' $NEWID)
  LE="${NH:6:2}${NH:4:2}${NH:2:2}${NH:0:2}"
  case "$IDS_HEX" in
    *$LE*) echo "id $NEWID already present";;
    *) IDS_HEX="$IDS_HEX$LE"; ADD="$ADD$LE"; NEWCNT=$((NEWCNT+1));;
  esac
done
if [ -z "$ADD" ]; then echo "NOTHING-TO-ADD"; exit 0; fi
NEWCNT_HEX=$(printf '%08x' $NEWCNT)
NEWCNT_LE="${NEWCNT_HEX:6:2}${NEWCNT_HEX:4:2}${NEWCNT_HEX:2:2}${NEWCNT_HEX:0:2}"
echo "new: count=$NEWCNT ids=$IDS_HEX"

HDR=$(dd if="$BIN" bs=1 skip=0 count=12 2>/dev/null | od -An -tx1 | tr -d ' \n')
TAIL_SKIP=$((16 + CNT*4))
TAIL_LEN=$((SIZE - TAIL_SKIP))
{
  echo "$HDR" | xxd -r -p
  printf "$(echo $NEWCNT_LE | sed 's/../\\x&/g')"
  echo "$IDS_HEX" | xxd -r -p
  dd if="$BIN" bs=1 skip=$TAIL_SKIP count=$TAIL_LEN 2>/dev/null
} > /data/local/tmp/hometweaks_new.bin
NEW_SIZE=$(wc -c < /data/local/tmp/hometweaks_new.bin)
EXPECT=$((SIZE + NEWCNT*4 - CNT*4))
echo "new size=$NEW_SIZE"
cp /data/local/tmp/hometweaks_new.bin "$BIN"
chmod 666 "$BIN"
echo BIN-UPDATED
