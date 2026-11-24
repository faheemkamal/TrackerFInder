"""Reads a Find Hub tag's battery level from its Bluetooth signal (added for Tracker Finder).

Google's servers don't report tag battery in the data this app gets. The tag itself puts a
battery level in its Bluetooth advert, hidden with a key only the owner has. When we locate a
tag we already decrypt its reports with that key (the "identity key"), so we keep the key and
the tag's clock offset here, and later, when the phone hears the tag nearby, we:
  1. work out which 1024-second window the tag's clock is in,
  2. recompute the tag's broadcast ID (EID) for that window and compare it with the advert,
  3. un-hide the "flags" byte: flags = hashed_flags XOR last byte of SHA-256(r).
Battery level is bits 5-6 of the flags (FMDN spec): 0 unsupported, 1 normal, 2 low, 3 critical.
"""
import hashlib
import json
import os
import time

from server.config import DATA_DIR

_PATH = DATA_DIR / "beacon_keys.json"
_LEVELS = {0: "unsupported", 1: "normal", 2: "low", 3: "critical"}
ROTATION = 1024


def _load() -> dict:
    try:
        return json.loads(_PATH.read_text())
    except Exception:
        return {}


def _save(d: dict):
    tmp = _PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(d))
    os.chmod(tmp, 0o600)
    tmp.replace(_PATH)


def remember(device_id: str, identity_key: bytes, beacon_time: int, report_ts: int):
    """Called while decrypting a location report (non-MCU tags only)."""
    if not device_id or not identity_key or beacon_time <= 0:
        return
    d = _load()
    cur = d.get(device_id)
    if cur and cur.get("ts", 0) >= report_ts and cur.get("eik") == identity_key.hex():
        return
    d[device_id] = {"eik": identity_key.hex(), "offset": int(beacon_time), "ts": int(report_ts)}
    _save(d)


def forget_all():
    try:
        _PATH.unlink()
    except FileNotFoundError:
        pass


def _eid_and_r(eik: bytes, t: int):
    from ecdsa import SECP160r1
    from FMDNCrypto.eid_generator import calculate_r
    r = calculate_r(eik, t)
    return (r * SECP160r1.generator).x().to_bytes(20, "big"), r


def decode_flags(hashed: int, r: int) -> int:
    return hashed ^ hashlib.sha256(r.to_bytes(20, "big")).digest()[-1]


def match(service_data_hex: str, now: float | None = None) -> str:
    """service data for UUID 0xFEAA: frame byte, 20-byte EID, optional hashed-flags byte.
    Returns JSON {id, battery, utp} for one of our tags, or "" if it isn't ours."""
    data = bytes.fromhex(service_data_hex)
    if len(data) < 21 or data[0] not in (0x40, 0x41):
        return ""
    eid = data[1:21]
    hashed = data[21] if len(data) > 21 else None
    now = time.time() if now is None else now
    for dev, k in _load().items():
        eik = bytes.fromhex(k["eik"])
        est = k["offset"] + int(now - k["ts"])
        for w in (0, -1, 1, -2, 2, -3, 3):
            t = est + w * ROTATION
            if t < 0:
                continue
            cand, r = _eid_and_r(eik, t)
            if cand != eid:
                continue
            if hashed is None:
                return json.dumps({"id": dev, "battery": "unsupported", "utp": data[0] == 0x41})
            flags = decode_flags(hashed, r)
            return json.dumps({"id": dev, "battery": _LEVELS[(flags >> 5) & 3], "utp": bool(flags & 0x80)})
    return ""
