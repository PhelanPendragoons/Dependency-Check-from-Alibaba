#!/usr/bin/env python3
"""
normalize-nvd-json.py — 规范化 NVD 镜像 JSON 的时间戳小数位

open-vulnerability-clients 的 CveItem 使用 @JsonFormat(pattern =
"uuuu-MM-dd'T'HH:mm:ss.SSS") 反序列化，只接受恰好 3 位小数且无时区偏移；
而 NVD 2.0 数据源中的 published/lastModified 形如
2026-07-15T03:03:10.8602999（1~7 位小数）或无小数，直接解析会抛
DateTimeParseException。本脚本把所有此类时间戳统一为恰好 3 位小数。

用法：python normalize-nvd-json.py <mirror-dir>
就地重写 <mirror-dir>/nvdcve-2.0-*.json.gz。
"""
import gzip
import re
import sys
from pathlib import Path

# 捕获 ISO 时间戳的秒段与可选小数段（JSON 字符串值内，后随引号）
TS = re.compile(r'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(\.\d+)?(?=")')


def fix(match: re.Match) -> str:
    frac = (match.group(2) or ".")[1:]  # 去掉小数点
    return f"{match.group(1)}.{(frac + '000')[:3]}"


def main() -> None:
    mirror = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    files = sorted(mirror.glob("nvdcve-2.0-*.json.gz"))
    if not files:
        sys.exit(f"[!!] {mirror} 下没有 nvdcve-2.0-*.json.gz")
    for gz in files:
        raw = gzip.decompress(gz.read_bytes()).decode("utf-8")
        fixed, n = TS.subn(fix, raw)
        gz.write_bytes(gzip.compress(fixed.encode("utf-8"), compresslevel=6))
        print(f"[OK] {gz.name}: 规范化 {n} 处时间戳", flush=True)
    print(f"[DONE] 共处理 {len(files)} 个文件")


if __name__ == "__main__":
    main()
