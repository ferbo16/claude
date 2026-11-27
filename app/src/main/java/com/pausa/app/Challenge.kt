package com.pausa.app

import kotlin.random.Random

/** Un problema de matemáticas con 4 opciones. La dificultad sube con cada extensión. */
data class Challenge(val text: String, val answer: Int, val options: List<Int>) {
    companion object {
        fun generate(difficulty: Int, rnd: Random = Random.Default): Challenge {
            val (text, answer) = when (difficulty.coerceIn(1, 4)) {
                1 -> {
                    val a = rnd.nextInt(12, 60); val b = rnd.nextInt(12, 40)
                    "$a + $b" to a + b
                }
                2 -> {
                    val a = rnd.nextInt(4, 13); val b = rnd.nextInt(4, 13)
                    "$a × $b" to a * b
                }
                3 -> {
                    val a = rnd.nextInt(4, 13); val b = rnd.nextInt(4, 13); val c = rnd.nextInt(11, 50)
                    "$a × $b + $c" to a * b + c
                }
                else -> {
                    val a = rnd.nextInt(13, 30); val b = rnd.nextInt(3, 10); val c = rnd.nextInt(11, 60)
                    "$a × $b − $c" to a * b - c
                }
            }
            val opts = mutableSetOf(answer)
            while (opts.size < 4) {
                val delta = rnd.nextInt(1, 11) * (if (rnd.nextBoolean()) 1 else -1)
                opts.add(answer + delta)
            }
            return Challenge("$text = ?", answer, opts.shuffled(rnd))
        }
    }
}
