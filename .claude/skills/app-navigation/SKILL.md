---
name: app-navigation
description: Reach a screen in the running app. Use before any tap, screenshot or UI check, to find which screen owns an element and how to get there.
---

# Navigating the app

`probe/scripts/probe nav-map` returns every screen of the app, its breadcrumb, the automation ids it
owns and the taps that lead out of it. It comes from `AppNavigationMap` in the debug build, so it is
as true as the code. Ask for it BEFORE anything else.

1. Find the screen that owns the element you care about: `probe/scripts/probe owner-of <id>`, or
   read the whole map. Never go looking for a screen by dumping the UI.
2. `probe/scripts/probe goto <screen>` to get there. Add `--index N` for a screen reached through a
   list row, e.g. `probe goto toy_detail --index 2`.
3. Only then `probe/scripts/probe ui-snapshot`, and only to check what you just changed.

## Rules

- Never tap a coordinate that did not come from a snapshot taken seconds ago.
- Never write a hard-coded route into a flow. Use `goto`, so the flow survives a screen being
  inserted in the middle of the path.
- A screen you just added is not done until it has a `Screen` entry in `AppNavigationMap`, with its
  ids and its exits, and its ids come from the `*Ids` object next to the composable, never from a
  string literal.
- `probe/scripts/run-flow --from-map` walks every edge of the map on the device. Run it after
  touching navigation: the compiler checks the ids, only this checks the arrows.

## Where the pieces live

- `app/src/debug/kotlin/dev/galex/toyapp/AppNavigationMap.kt` the map itself
- `app/src/main/kotlin/dev/galex/toyapp/ui/AutomationIds.kt` the id constants both sides share
- `probe-server/src/main/kotlin/dev/galex/toyapp/probe/NavigationMap.kt` the types and `GET /nav_map`
- `probe/scripts/nav_map.py` routing, placeholder filling and arrival checks
