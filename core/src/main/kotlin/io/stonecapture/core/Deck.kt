package io.stonecapture.core

import kotlin.random.Random

fun fullDeck(): List<Card> =
    CardColor.entries.flatMap { color -> (Card.MIN_RANK..Card.MAX_RANK).map { Card(color, it) } }

fun shuffledDeck(random: Random): List<Card> = fullDeck().shuffled(random)
