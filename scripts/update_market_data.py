#!/usr/bin/env python3
"""Build the public, non-personal market feed for Investment OS.

No portfolio/account data is read. The script fetches only generic public market
symbols and writes market-data.json for GitHub Pages.
"""
from __future__ import annotations

import json
import math
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "market-data.json"

SYMBOLS = {
    "sp500": "^GSPC",
    "kospi": "^KS11",
    "usdkrw": "KRW=X",
    "us10y": "^TNX",
}

def fetch_chart(symbol: str) -> list[float]:
    encoded = urllib.parse.quote(symbol, safe="")
    last_error = None
    for host in ("query1.finance.yahoo.com", "query2.finance.yahoo.com"):
        url = (
            f"https://{host}/v8/finance/chart/{encoded}"
            "?range=1y&interval=1d&events=history&includeAdjustedClose=true"
        )
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "Mozilla/5.0 InvestmentOS-MarketBot/2.0",
                "Accept": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            result = payload["chart"]["result"][0]
            closes = result["indicators"]["quote"][0]["close"]
            values = [float(x) for x in closes if x is not None and math.isfinite(float(x))]
            if len(values) < 2:
                raise RuntimeError("insufficient close history")
            return values
        except Exception as exc:
            last_error = exc
            time.sleep(1)
    raise RuntimeError(f"{symbol}: {last_error}")

def point(values: list[float], with_regime: bool) -> dict:
    latest = values[-1]
    prev = values[-2]
    change = ((latest / prev) - 1.0) * 100.0 if prev else None
    dma200 = sum(values[-200:]) / 200.0 if len(values) >= 200 else None
    distance = ((latest / dma200) - 1.0) * 100.0 if dma200 else None
    regime = "N/A"
    if with_regime and distance is not None:
        regime = "RISK-ON" if distance > 1.0 else "RISK-OFF" if distance < -1.0 else "NEUTRAL"
    return {
        "value": round(latest, 4),
        "change_pct": round(change, 3) if change is not None else None,
        "dma200": round(dma200, 4) if dma200 is not None else None,
        "distance_200dma_pct": round(distance, 3) if distance is not None else None,
        "regime": regime,
        "status": "ok",
    }

def main() -> int:
    old = {}
    if OUT.exists():
        try:
            old = json.loads(OUT.read_text(encoding="utf-8"))
        except Exception:
            old = {}

    result = {
        "schema": "investment-os-market-v1",
        "generated_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "source": "Yahoo Finance chart endpoint via GitHub Actions; best-effort public quotes",
        "privacy": "No portfolio, holdings, balances, device identifiers, or account data are processed.",
    }

    ok = 0
    errors = {}
    for key, symbol in SYMBOLS.items():
        try:
            result[key] = point(fetch_chart(symbol), with_regime=key in ("sp500", "kospi"))
            ok += 1
        except Exception as exc:
            errors[key] = str(exc)
            fallback = old.get(key) or {
                "value": None,
                "change_pct": None,
                "dma200": None,
                "distance_200dma_pct": None,
                "regime": "UNKNOWN" if key in ("sp500", "kospi") else "N/A",
            }
            fallback["status"] = "stale"
            result[key] = fallback

    result["errors"] = errors
    if ok == 0:
        print("No market symbols refreshed; refusing to overwrite feed.", file=sys.stderr)
        return 1

    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Updated {ok}/{len(SYMBOLS)} symbols -> {OUT}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
