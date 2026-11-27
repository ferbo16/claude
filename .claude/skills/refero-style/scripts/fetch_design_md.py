#!/usr/bin/env python3
"""Descarga el DESIGN.md de un estilo de Refero Styles.

Uso:
    python3 fetch_design_md.py <URL de styles.refero.design/style/...> [archivo_salida]

Por defecto guarda en design/DESIGN.md (relativo a donde lo ejecutes).
"""
import html
import os
import re
import sys
import urllib.request

ALLOWED = re.compile(r"^https://styles\.refero\.design/style/[0-9a-fA-F-]{36}/?$")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    url = sys.argv[1].strip().split("?")[0].split("#")[0]
    if not ALLOWED.match(url):
        print("Error: el enlace debe ser de la forma https://styles.refero.design/style/<id>")
        return 1
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join("design", "DESIGN.md")

    req = urllib.request.Request(url, headers={"User-Agent": "design-md-fetcher"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        page = resp.read().decode("utf-8", errors="replace")

    # El DESIGN.md viene incrustado en la página dentro de un bloque <code>.
    design_md = None
    for block in re.findall(r"<code[^>]*>(.*?)</code>", page, re.S):
        text = html.unescape(re.sub(r"<[^>]+>", "", block))
        if text.lstrip().startswith("# ") and "Style Reference" in text.split("\n", 1)[0]:
            design_md = text.strip() + "\n"
            break
    if not design_md:
        print("Error: no encontré el DESIGN.md en esa página (¿cambió el sitio?).")
        return 2

    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(f"<!-- Fuente: {url} (generado por Refero Styles, no es guía oficial de la marca) -->\n")
        f.write(design_md)
    title = design_md.split("\n", 1)[0].lstrip("# ").strip()
    print(f"OK: '{title}' guardado en {out} ({len(design_md)} caracteres)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
