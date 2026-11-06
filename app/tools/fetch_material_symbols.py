#!/usr/bin/env python3
"""
Downloads the Material Symbols (Outlined, weight 400, 24px) vector icons TutorSync uses
from Google's official repo into app/src/main/res/drawable as ms_<name>.xml (and ms_<name>_fill.xml).
Apache License 2.0. Run once from anywhere:   python app/tools/fetch_material_symbols.py
"""

import concurrent.futures as cf
import os
import urllib.request

BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android"
OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "res", "drawable"
)

OUTLINED = """arrow_back arrow_forward close check add search visibility visibility_off lock mail person person_add
group groups menu_book calendar_month receipt_long payments event_available event_busy event_repeat fact_check settings
home school call edit delete more_vert chevron_right expand_more expand_less filter_list info warning error check_circle
schedule history language palette brightness_6 dark_mode light_mode fingerprint pin key logout backup download upload share
picture_as_pdf notifications account_circle badge undo content_copy help translate paid account_balance_wallet
request_quote trending_up savings photo_camera image send cloud_off person_off search_off inbox location_on tune sort
today add_circle cancel block lock_reset shield delete_forever timer folder contacts book bolt""".split()
FILLED = "home groups settings event_available receipt_long fact_check payments group dashboard check_circle error warning cancel".split()


def fetch(job):
    name, filled = job
    suffix = "_fill1" if filled else ""
    url = f"{BASE}/{name}/materialsymbolsoutlined/{name}{suffix}_24px.xml"
    target = os.path.join(OUT, f"ms_{name}{'_fill' if filled else ''}.xml")
    try:
        data = urllib.request.urlopen(url, timeout=30).read().decode("utf-8")
        with open(target, "w", encoding="utf-8", newline="\n") as f:
            f.write(data if data.endswith("\n") else data + "\n")
        return None
    except Exception as e:  # noqa: BLE001
        return f"{name}{suffix}: {e}"


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    jobs = [(n, False) for n in OUTLINED] + [(n, True) for n in FILLED]
    with cf.ThreadPoolExecutor(8) as pool:
        errors = [r for r in pool.map(fetch, jobs) if r]
    print(
        f"Downloaded {len(jobs) - len(errors)} of {len(jobs)} icons to {os.path.abspath(OUT)}"
    )
    for e in errors:
        print("FAILED", e)
