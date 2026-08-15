package dev.galex.toyapp

import dev.galex.toyapp.probe.Action
import dev.galex.toyapp.probe.NavigationMap
import dev.galex.toyapp.probe.Screen
import dev.galex.toyapp.ui.ToyDetailIds
import dev.galex.toyapp.ui.ToysIds

/**
 * Every screen of this app, its breadcrumb, the ids it owns and the taps that lead out of it.
 *
 * It lives in src/debug because it speaks the probe's types, and the probe arrives through
 * `debugImplementation`: there is no navigation map in a release build, and nothing to strip.
 *
 * The ids are not written out by hand here. They come from the same constants the composables pass
 * to `Modifier.automationId`, so renaming one breaks this file at compile time instead of quietly
 * sending our agent to a tap that lands nowhere.
 */
val AppNavigationMap = NavigationMap(
    screens = listOf(
        Screen(
            id = "toys",
            breadcrumb = "Toys",
            entry = true,
            ids = listOf(
                ToysIds.Title,
                ToysIds.List,
                ToysIds.card(INDEX),
            ),
            actions = listOf(
                Action(
                    tapId = ToysIds.card(INDEX),
                    leadsTo = "toy_detail",
                ),
            ),
        ),
        Screen(
            id = "toy_detail",
            // The toy id is data, so it stays a placeholder: goto checks the shape, not the toy.
            breadcrumb = "ToyDetail({toyId})",
            ids = listOf(
                ToyDetailIds.Name,
                ToyDetailIds.Meta,
                ToyDetailIds.Description,
                ToyDetailIds.BackButton,
            ),
            actions = listOf(
                Action(tapId = ToyDetailIds.BackButton, leadsTo = "toys"),
            ),
        ),
    ),
)

/** One entry describes all six rows of the list, so the index is left to be filled in. */
private const val INDEX = "{index}"
