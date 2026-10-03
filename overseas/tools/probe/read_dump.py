#!/usr/bin/env python3
"""Turn probe ScreenDumps of a WhatsApp chat into message rows.

Usage: python3 read_dump.py dump-0001.json [dump-0002.json ...]

A prototype of the T2 parsing rules, used to check the T1 recordings by eye.
It reads resource IDs only (WhatsApp 2.26.38.73); nothing here is final.
"""
import json
import sys

WA = "com.whatsapp:id/"


def children_of(nodes):
    kids = {}
    for n in nodes:
        kids.setdefault(n["parent"], []).append(n)
    return kids


def descendants(node, kids):
    out = []
    stack = list(reversed(kids.get(node["i"], [])))
    while stack:
        n = stack.pop()
        out.append(n)
        stack.extend(reversed(kids.get(n["i"], [])))
    return out


def find(items, suffix):
    return [n for n in items if (n["id"] or "") == WA + suffix]


def text_of(items, suffix):
    hit = find(items, suffix)
    return hit[0]["text"] if hit else None


def parse(path):
    dump = json.load(open(path, encoding="utf-8"))
    nodes = dump["nodes"]
    kids = children_of(nodes)
    width = dump["meta"]["screen"]["width"]
    lists = [n for n in nodes if n["id"] == "android:id/list" and "scrollable" in n["flags"]]
    if not lists:
        return dump, []
    rows = []
    for row in kids.get(lists[0]["i"], []):
        sub = descendants(row, kids)
        main = find(sub, "main_layout")
        row_id = (row["id"] or "").replace(WA, "") or "(no id)"
        kind = {
            "conversation_row_text": "text",
            "conversation_row_image": "image",
            "conversation_row_voice_note": "voice",
        }.get(row_id, "other")
        side = "unknown"
        if main:
            left, _, right, _ = main[0]["bounds"]
            margin_left, margin_right = left, width - right
            if margin_left < margin_right:
                side = "other"
            elif margin_right < margin_left:
                side = "self"
        has_status = bool(find(sub, "status"))
        body = text_of(sub, "message_text")
        if kind == "other" and find(sub, "icon") and body:
            kind = "deleted"
        if kind == "voice":
            body = "[voice %s]" % (text_of(sub, "description") or "?")
        if kind == "image":
            body = "[image]" + (" " + body if body else "")
        rows.append({
            "row": row_id,
            "kind": kind,
            "side": side,
            "status_icon": has_status,
            "text": body,
            "time": text_of(sub, "date"),
            "edited": bool(find(sub, "edit_label")),
            "quote_from": text_of(sub, "quoted_title"),
            "quote_text": text_of(sub, "quoted_text"),
            "row_bounds": row["bounds"],
            "main_bounds": main[0]["bounds"] if main else None,
        })
    return dump, rows


def main(paths):
    for path in paths:
        dump, rows = parse(path)
        meta = dump["meta"]
        print("== %s  mode=%s  rows=%d" % (path, meta["mode"], len(rows)))
        for r in rows:
            extra = []
            if r["edited"]:
                extra.append("edited")
            if r["quote_text"] is not None:
                extra.append("quotes[%s]: %r" % (r["quote_from"], r["quote_text"]))
            if r["status_icon"]:
                extra.append("status-icon")
            print("  %-5s %-7s %-6s %r %s" % (r["side"], r["kind"], r["time"], r["text"], " | ".join(extra)))
            print("        row %s main %s" % (r["row_bounds"], r["main_bounds"]))


if __name__ == "__main__":
    main(sys.argv[1:])
