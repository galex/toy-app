"""Routing over the app's navigation map.

The app declares its screens in Kotlin and the probe serves them on GET /nav_map. This module is
the client side of that: find a screen, work out the taps that reach it, and check that we really
landed there.

It exists so that an agent never has to discover the app by dumping the UI. It asks the map where a
screen is, walks there, and only then looks at the screen it came for.

Standard library only, like everything else under probe/scripts.
"""
from __future__ import annotations

import re
import time
from collections import deque

from probe_client import ProbeClient, ProbeError

# How long we wait for the breadcrumb to catch up after a tap. Navigation is asynchronous, so we
# poll instead of sleeping: a sleep long enough today is a flake on a slower machine tomorrow.
ARRIVE_TIMEOUT_S = 4.0
ARRIVE_POLL_S = 0.15

PLACEHOLDER = re.compile(r"\{([a-zA-Z_][a-zA-Z0-9_]*)\}")


def load_map(client: ProbeClient) -> dict:
    """The navigation map as the running app declares it."""
    return client.nav_map()


def screen(nav_map: dict, screen_id: str) -> dict:
    for candidate in nav_map.get("screens", []):
        if candidate.get("id") == screen_id:
            return candidate
    known = ", ".join(s.get("id", "?") for s in nav_map.get("screens", [])) or "none"
    raise ProbeError(f"no screen {screen_id!r} in the navigation map. Known screens: {known}")


def entry_screen(nav_map: dict) -> dict:
    for candidate in nav_map.get("screens", []):
        if candidate.get("entry"):
            return candidate
    raise ProbeError("the navigation map declares no entry screen (Screen(entry = true))")


def owner_of(nav_map: dict, element_id: str) -> dict | None:
    """Which screen owns this automation id, placeholders included."""
    for candidate in nav_map.get("screens", []):
        for known in candidate.get("ids", []):
            if known == element_id or _matches(known, element_id):
                return candidate
    return None


def route(nav_map: dict, target_id: str, from_id: str | None = None) -> list[dict]:
    """The shortest list of actions from where we are to [target_id].

    Breadth first over `leadsTo`, so nothing in the map has to spell a route out by hand: adding a
    screen with one action is enough for every route through it to exist. [from_id] defaults to the
    entry screen, which is where the app starts.
    """
    start = screen(nav_map, from_id) if from_id else entry_screen(nav_map)
    if start["id"] == target_id:
        return []

    seen = {start["id"]}
    queue: deque[tuple[dict, list[dict]]] = deque([(start, [])])
    while queue:
        current, path = queue.popleft()
        for action in current.get("actions", []):
            nxt = action.get("leadsTo")
            if nxt in seen:
                continue
            step = dict(action, fromScreen=current["id"])
            if nxt == target_id:
                return path + [step]
            seen.add(nxt)
            queue.append((screen(nav_map, nxt), path + [step]))

    raise ProbeError(
        f"no route to {target_id!r} from {start['id']!r}: no chain of actions in the map leads "
        f"there. Add the missing Action(tapId = ..., leadsTo = ...) to the screen it comes from."
    )


def leaf(breadcrumb: str) -> str:
    """The screen we are on right now, out of a trail like 'Toys > ToyDetail(building-blocks)'."""
    return breadcrumb.split(">")[-1].strip()


def screen_at(nav_map: dict, breadcrumb: str) -> dict | None:
    """Which screen of the map are we standing on, according to the breadcrumb?"""
    here = leaf(breadcrumb)
    for candidate in nav_map.get("screens", []):
        if _matches(candidate.get("breadcrumb", ""), here):
            return candidate
    return None


def resolve(template: str, params: dict[str, object]) -> str:
    """Fill `{index}` style placeholders in an id, and refuse to tap one we did not fill."""
    def replace(match: re.Match) -> str:
        name = match.group(1)
        if name not in params:
            raise ProbeError(
                f"{template!r} needs a value for {{{name}}}. Pass it, for example --{name} 2."
            )
        return str(params[name])

    return PLACEHOLDER.sub(replace, template)


def _matches(template: str, actual: str) -> bool:
    """Does a concrete id or breadcrumb match a template that carries placeholders?"""
    pattern = "".join(
        ".+" if PLACEHOLDER.fullmatch(part) else re.escape(part)
        for part in re.split(r"(\{[a-zA-Z_][a-zA-Z0-9_]*\})", template)
    )
    return re.fullmatch(pattern, actual) is not None


def breadcrumb_matches(template: str, actual: str) -> bool:
    """Are we ON this screen?

    The map holds one screen's breadcrumb and the app reports the whole trail, so we compare against
    the LAST part of it. Matching anywhere in the trail would make 'Toys' pass while we are sitting
    on 'Toys > ToyDetail(building-blocks)', which is exactly the lie a map must never tell.
    """
    if not template:
        return True
    return _matches(template, leaf(actual))


def current_breadcrumb(client: ProbeClient) -> str:
    return client.ui_snapshot().get("breadcrumb", "")


def find_element(client: ProbeClient, element_id: str) -> dict | None:
    """Match on the bare id, so the map never has to write the "<pkg>:id/" prefix a snapshot carries."""
    want = element_id.rsplit(":id/", 1)[-1]
    for element in client.ui_snapshot().get("elements", []):
        if (element.get("id") or "").rsplit(":id/", 1)[-1] == want:
            return element
    return None


def goto(client: ProbeClient, target_id: str, params: dict[str, object], on_step=None) -> dict:
    """Walk the map to [target_id], tapping our way and checking the breadcrumb at every hop.

    Returns where we ended up and how many taps it took. Progress lines go to [on_step] as they
    happen, so a long walk stays readable while it runs.
    """
    nav_map = load_map(client)
    destination = screen(nav_map, target_id)
    log: list[str] = []

    def report(line: str) -> None:
        log.append(line)
        if on_step:
            on_step(line)

    # Route from where we ARE, not from where the app started. Otherwise "go to the list" while
    # standing on the detail screen resolves to zero hops and reports an arrival that never
    # happened.
    here = current_breadcrumb(client)
    standing_on = screen_at(nav_map, here)
    steps = route(nav_map, target_id, from_id=standing_on["id"] if standing_on else None)
    report(f"goto {target_id!r}: {len(steps)} hop(s) from {here!r}")

    for step in steps:
        tap_id = resolve(step["tapId"], params)
        element = find_element(client, tap_id)
        if element is None:
            raise ProbeError(
                f"goto {target_id!r}: {tap_id!r} is not on screen (we are at {here!r}). The map "
                f"says it should be on {step['fromScreen']!r}."
            )
        cx = element["x"] + element["width"] / 2
        cy = element["y"] + element["height"] / 2
        client.tap(cx, cy)
        report(f"  ok   tap_id {tap_id!r} -> ({cx:.0f},{cy:.0f})")

        expected = screen(nav_map, step["leadsTo"])["breadcrumb"]
        here = _await_breadcrumb(client, expected)
        if here is None:
            raise ProbeError(
                f"goto {target_id!r}: tapped {tap_id!r} but never arrived at {expected!r} "
                f"(we are at {current_breadcrumb(client)!r}). The map's leadsTo is wrong, or the "
                f"navigation is."
            )
        report(f"  ok   arrived at {here!r}")

    if not steps:
        # Claiming an arrival is the one thing a map must never get wrong, so we check even when we
        # believe we never moved.
        arrived = _await_breadcrumb(client, destination["breadcrumb"])
        if arrived is None:
            raise ProbeError(
                f"goto {target_id!r}: we look like we are already there, but the app reports "
                f"{current_breadcrumb(client)!r}. The map's breadcrumb for this screen is wrong."
            )
        here = arrived
        report(f"  ok   already at {here!r}")
    return {"screen": target_id, "hops": len(steps), "breadcrumb": here, "log": log}


def _await_breadcrumb(client: ProbeClient, expected: str) -> str | None:
    deadline = time.time() + ARRIVE_TIMEOUT_S
    while True:
        actual = current_breadcrumb(client)
        if breadcrumb_matches(expected, actual):
            return actual
        if time.time() >= deadline:
            return None
        time.sleep(ARRIVE_POLL_S)


def edges(nav_map: dict) -> list[dict]:
    """Every action in the map, as `from` / `tapId` / `leadsTo`, for the --from-map walk."""
    return [
        dict(action, fromScreen=s["id"])
        for s in nav_map.get("screens", [])
        for action in s.get("actions", [])
    ]
