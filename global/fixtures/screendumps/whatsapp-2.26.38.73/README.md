# ScreenDumps: WhatsApp 2.26.38.73, OnePlus PJD110, Android 16

Recorded 2026-10-02 with the read-only probe (`:probe`, schema `jev.screendump/1`)
from a dedicated test chat between two test accounts, following a fixed script of
made-up messages. WhatsApp UI language zh-CN.

The contact's name and status are not stored: text outside the message list is
dropped and the name is replaced by `<TOPBAR>` where it recurs inside the list.

| File | Screen | Mode | Notes |
|---|---|---|---|
| dump-0001.json | Chat, bottom (image … message 17) | full | Probe t1-0.1: the times of the last two messages were masked by mistake. Use dump-0004 instead. |
| dump-0002.json | Chat, middle | full | Taken while the list was still moving: row bounds and child bounds disagree by about 280 px. |
| dump-0003.json | Chat, top (date divider, encryption notice, messages 1–9) | full | Settled. |
| dump-0004.json | Chat, bottom | full | Settled (`stability.rowsMoved` 0). Reference for the bottom screen. |
| dump-0005.json | Chat, top | full | Settled. Reference for the top screen. |
| dump-0006.json | Chat, during a fling | full | `rowsMoved` 7, `maxDeltaPx` 766; row count changed 7 to 9 during the walk. |
| dump-0007.json | Chat, during a fling | full | `rowsMoved` 10, `maxDeltaPx` 491; the last row has no children. |
| dump-0008.json | Chat list | structure | No text at all. For telling the list page from a chat page. |

`python3 tools/probe/read_dump.py <files>` prints the messages a dump yields.
