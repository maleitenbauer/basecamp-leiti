package com.markus.basecamp.gaming.cs2

/** Starting content, taken from the coach's notes. Created once per user, then fully editable. */
object Cs2Defaults {

    class RoutineDefault(
        val title: String,
        val description: String,
        val category: RoutineCategory,
        val targetMinutes: Int? = null,
    )

    class PrincipleDefault(
        val title: String,
        val body: String,
        val category: PrincipleCategory,
        val pinned: Boolean = false,
    )

    val routine = listOf(
        RoutineDefault(
            title = "Ignorant tapping on aimbots",
            description = "Go to the next target before checking if the first is dead. On aimbots for at least 2 weeks, " +
                "15-20 minutes before or after play. When confident, move to deathmatch.",
            category = RoutineCategory.WARMUP,
            targetMinutes = 20,
        ),
        RoutineDefault(
            title = "Prefire maps",
            description = "Warm up on two different prefire maps before a session. This also trains faster clears.",
            category = RoutineCategory.WARMUP,
        ),
        RoutineDefault(
            title = "Watch pro gameplay",
            description = "Before playing on any day, watch 10 minutes of T1 pro league or T1 pro FACEIT gameplay.",
            category = RoutineCategory.WARMUP,
            targetMinutes = 10,
        ),
        RoutineDefault(
            title = "Isolating duels in deathmatch",
            description = "Play deathmatch in open areas like mid or A site on Mirage. Use the available cover as well " +
                "as you can to isolate fights.",
            category = RoutineCategory.PRACTICE,
        ),
        RoutineDefault(
            title = "Faster clears",
            description = "Clear angles faster. Do the prefire map warmup first, then practice it in games.",
            category = RoutineCategory.PRACTICE,
        ),
        RoutineDefault(
            title = "Utility with a purpose",
            description = "Before throwing, name the problem the utility solves (for example the stairs flash). " +
                "No random throws.",
            category = RoutineCategory.PRACTICE,
        ),
    )

    val principles = listOf(
        PrincipleDefault(
            title = "Focus: be ready for your duels",
            body = "This is the most important behaviour to pick up for performance. No tip will do more for you than " +
                "focusing your effort. Be prepared for what is going to happen.",
            category = PrincipleCategory.MINDSET,
            pinned = true,
        ),
        PrincipleDefault(
            title = "Avoid W in duels",
            body = "Avoid W as much as physically possible. When holding W you move slower from your enemy's " +
                "perspective, and it is much harder to counterstrafe.",
            category = PrincipleCategory.MOVEMENT,
            pinned = true,
        ),
        PrincipleDefault(
            title = "Utility with a purpose",
            body = "Think about the problem, then how utility can solve it, instead of throwing randomly and hoping for " +
                "results. Example: show stairs flash.",
            category = PrincipleCategory.UTILITY,
        ),
        PrincipleDefault(
            title = "Ignorant tapping",
            body = "Go to the next target before checking if the first is dead. Aimbots for at least 2 weeks, 15-20 " +
                "minutes before or after play. When confident, move to deathmatch.",
            category = PrincipleCategory.AIM,
        ),
        PrincipleDefault(
            title = "Isolating duels",
            body = "Play deathmatch in open areas like mid or A site on Mirage, using the cover available to isolate fights.",
            category = PrincipleCategory.PRACTICE,
        ),
        PrincipleDefault(
            title = "Faster clears",
            body = "Prefire map warmup, then practice.",
            category = PrincipleCategory.PRACTICE,
        ),
        PrincipleDefault(
            title = "Prefire maps before a session",
            body = "Practice two different prefire maps before you play.",
            category = PrincipleCategory.PREPARATION,
        ),
        PrincipleDefault(
            title = "Watch pro gameplay first",
            body = "Before playing on any day, watch 10 minutes of T1 pro league or T1 pro FACEIT gameplay.",
            category = PrincipleCategory.PREPARATION,
        ),
    )
}
