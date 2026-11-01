package io.stonecapture.core

/** Parses "R5" style card codes: color initial (R B G Y P O) followed by the rank. */
fun c(code: String): Card {
    val color = CardColor.entries.single { it.name.first() == code[0] }
    return Card(color, code.substring(1).toInt())
}

fun cards(codes: String): List<Card> = codes.split(" ").filter { it.isNotBlank() }.map(::c)

fun side(codes: String, completedAt: Int? = null): Side {
    val list = cards(codes)
    return Side(list, completedAt ?: if (list.size == SIDE_CAPACITY) 1 else null)
}

fun stones(vararg stones: Stone): List<Stone> =
    List(STONE_COUNT) { i -> stones.firstOrNull { it.index == i } ?: Stone(i) }
