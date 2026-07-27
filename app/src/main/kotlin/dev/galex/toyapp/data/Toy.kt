package dev.galex.toyapp.data

data class Toy(
    val id: String,
    val name: String,
    val category: String,
    val ageRange: String,
    val pieces: Int,
    val description: String,
)

/** The whole "backend" of this demo app. */
val toys = listOf(
    Toy(
        id = "wooden-train",
        name = "Wooden Train",
        category = "Vehicles",
        ageRange = "3+",
        pieces = 12,
        description = "A little wooden locomotive with three carriages and a bag of magnetic " +
            "couplings. Rolls on anything flat and on quite a lot that isn't.",
    ),
    Toy(
        id = "rubber-duck",
        name = "Rubber Duck",
        category = "Bath",
        ageRange = "0+",
        pieces = 1,
        description = "Floats, squeaks, and has debugged more code than most senior engineers.",
    ),
    Toy(
        id = "building-blocks",
        name = "Building Blocks",
        category = "Construction",
        ageRange = "2+",
        pieces = 48,
        description = "Forty-eight blocks in six colours. Statistically, two of them are already " +
            "under the sofa.",
    ),
    Toy(
        id = "spinning-top",
        name = "Spinning Top",
        category = "Classic",
        ageRange = "4+",
        pieces = 1,
        description = "Spins for about eleven seconds, which is exactly ten seconds longer than " +
            "anyone expects.",
    ),
    Toy(
        id = "puzzle-cube",
        name = "Puzzle Cube",
        category = "Puzzles",
        ageRange = "6+",
        pieces = 26,
        description = "Six faces, one solution, and a sticker that will eventually be peeled off " +
            "in frustration.",
    ),
    Toy(
        id = "toy-robot",
        name = "Toy Robot",
        category = "Electronics",
        ageRange = "5+",
        pieces = 1,
        description = "Walks forward, blinks, and falls over on carpet. Batteries very much not " +
            "included.",
    ),
)

fun toyById(id: String): Toy? = toys.firstOrNull { it.id == id }
