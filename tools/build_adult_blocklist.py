#!/usr/bin/env python3
"""
Regenerates app/src/main/assets/blocklists/adult_domains.bin, the built-in adult-website list
behind "Block Adult Websites" (see mo.dev.ctrus.blocking.AdultDomainList).

Source: The Block List Project's porn list, domains-only variant
  https://github.com/blocklistproject/Lists (public domain / MIT, see NOTICE.txt)

Format: every domain is lowercased, a leading "www." dropped, and subdomains already covered by
a listed parent removed. Each remaining domain is stored as its 64-bit FNV-1a hash (UTF-8 bytes),
sorted as signed 64-bit integers, written big-endian with no header. The app binary-searches
this file straight from the APK (stored uncompressed), so nothing is loaded into memory. With
~1M entries, the chance of a random host colliding with one is ~1 in 10^13.

Usage: python3 tools/build_adult_blocklist.py [path-or-url]
"""
import struct
import sys
import urllib.request

DEFAULT_URL = "https://raw.githubusercontent.com/blocklistproject/Lists/master/alt-version/porn-nl.txt"
OUT = "app/src/main/assets/blocklists/adult_domains.bin"

FNV_OFFSET = 0xCBF29CE484222325
FNV_PRIME = 0x100000001B3


def fnv1a64(text: str) -> int:
    h = FNV_OFFSET
    for b in text.encode("utf-8"):
        h ^= b
        h = (h * FNV_PRIME) & 0xFFFFFFFFFFFFFFFF
    return h - (1 << 64) if h >= (1 << 63) else h  # as a signed Long, like Kotlin


def main() -> None:
    source = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_URL
    if source.startswith("http"):
        lines = urllib.request.urlopen(source).read().decode("utf-8", "ignore").splitlines()
    else:
        lines = open(source, encoding="utf-8", errors="ignore").read().splitlines()

    domains = set()
    for line in lines:
        d = line.strip().lower()
        if not d or d.startswith("#"):
            continue
        if d.startswith("www."):
            d = d[4:]
        domains.add(d)

    def covered(d: str) -> bool:
        parts = d.split(".")
        return any(".".join(parts[i:]) in domains for i in range(1, len(parts) - 1))

    hashes = sorted({fnv1a64(d) for d in domains if not covered(d)})
    with open(OUT, "wb") as f:
        for h in hashes:
            f.write(struct.pack(">q", h))
    print(f"{len(domains)} domains -> {len(hashes)} entries -> {OUT} ({len(hashes) * 8 / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
