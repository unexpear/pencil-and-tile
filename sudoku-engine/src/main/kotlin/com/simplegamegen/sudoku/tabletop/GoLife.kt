package com.simplegamegen.sudoku.tabletop

/**
 * Reads life and death off the board in front of it.
 * A group is left alone when it is unconditionally alive, and marked dead only when
 * a short local reading shows the opponent can capture it by moving first.
 * Nothing is looked up from a saved game.
 */
internal object GoLife {
    fun dead(board: List<Int>, size: Int): List<Int> {
        val alive = unconditionallyAlive(board, size, Go.BLACK) + unconditionallyAlive(board, size, Go.WHITE)
        val marked = ArrayList<Int>()
        val seen = BooleanArray(board.size)
        for (start in board.indices) {
            val color = board[start]
            if (color == 0 || seen[start] || start in alive) continue
            val group = chain(board, size, start)
            group.forEach { seen[it] = true }
            if (group.any { it in alive }) continue
            if (liberties(board, size, group).size > 4) continue
            if (attackerCaptures(board, size, group, color)) marked.addAll(group)
        }
        return marked.sorted()
    }

    /** Chains that stay alive even if the opponent is allowed to keep playing in their area. */
    private fun unconditionallyAlive(board: List<Int>, size: Int, color: Int): Set<Int> {
        val chains = chains(board, size, color).toMutableList()
        val regions = enclosed(board, size, color).toMutableList()
        var changed = true
        while (changed) {
            changed = false
            val kept = chains.filter { chain -> regions.count { vital(board, size, chain, it) } >= 2 }
            if (kept.size != chains.size) {
                chains.clear()
                chains.addAll(kept)
                changed = true
            }
            val inside = chains.flatten().toSet()
            val still = regions.filter { region ->
                neighborsOf(board, size, region).none { board[it] == color && it !in inside }
            }
            if (still.size != regions.size) {
                regions.clear()
                regions.addAll(still)
                changed = true
            }
        }
        return chains.flatten().toSet()
    }

    private fun vital(board: List<Int>, size: Int, chain: Set<Int>, region: Set<Int>): Boolean {
        val libs = liberties(board, size, chain)
        if (!region.all { it in libs }) return false
        if (region.size == 1) return realEye(board, size, region.first(), board[chain.first()])
        return true
    }

    /** A one-point eye whose diagonals are not open to the opponent. */
    private fun realEye(board: List<Int>, size: Int, point: Int, color: Int): Boolean {
        val orthogonal = around(size, point)
        if (orthogonal.isEmpty() || orthogonal.any { board[it] != color }) return false
        val enemy = diagonals(size, point).count { board[it] == -color }
        return when (orthogonal.size) {
            4 -> enemy <= 1
            else -> enemy == 0
        }
    }

    private fun attackerCaptures(board: List<Int>, size: Int, group: Set<Int>, color: Int): Boolean {
        val budget = intArrayOf(500)
        return read(board, size, group, color, attacker = true, depth = 6, budget)
    }

    private fun read(
        board: List<Int>, size: Int, group: Set<Int>, color: Int, attacker: Boolean, depth: Int, budget: IntArray,
    ): Boolean {
        if (group.none { board[it] == color }) return true
        if (depth == 0 || budget[0] <= 0) return false
        budget[0]--
        val moves = localMoves(board, size, group, color)
        if (attacker) {
            for (move in moves) {
                val next = play(board, size, move, -color) ?: continue
                if (read(next, size, group, color, false, depth - 1, budget)) return true
            }
            return false
        }
        val answers = moves.mapNotNull { play(board, size, it, color) }
        if (answers.isEmpty()) return read(board, size, group, color, true, depth - 1, budget)
        return answers.all { read(it, size, group, color, true, depth - 1, budget) }
    }

    private fun localMoves(board: List<Int>, size: Int, group: Set<Int>, color: Int): List<Int> {
        val near = HashSet<Int>()
        for (stone in group) if (board[stone] == color) {
            for (lib in around(size, stone)) if (board[lib] == 0) {
                near.add(lib)
                for (n in around(size, lib)) if (board[n] == 0) near.add(n)
            }
        }
        return near.sortedBy { libertiesAfter(board, size, it, -color, group) }
    }

    private fun libertiesAfter(board: List<Int>, size: Int, point: Int, color: Int, group: Set<Int>): Int {
        val next = play(board, size, point, color) ?: return 99
        val left = group.filter { next[it] == -color }
        return if (left.isEmpty()) -1 else liberties(next, size, left.toSet()).size
    }

    private fun play(board: List<Int>, size: Int, point: Int, color: Int): List<Int>? {
        if (point !in board.indices || board[point] != 0) return null
        val next = board.toMutableList()
        next[point] = color
        val doomed = HashSet<Int>()
        for (n in around(size, point)) {
            if (next[n] != -color) continue
            val (stones, libs) = flood(next, size, n)
            if (libs == 0) doomed.addAll(stones)
        }
        doomed.forEach { next[it] = 0 }
        if (flood(next, size, point).second == 0) return null
        return next
    }

    private fun chains(board: List<Int>, size: Int, color: Int): List<Set<Int>> {
        val seen = BooleanArray(board.size)
        return buildList {
            for (i in board.indices) if (board[i] == color && !seen[i]) {
                val group = chain(board, size, i)
                group.forEach { seen[it] = true }
                add(group)
            }
        }
    }

    private fun enclosed(board: List<Int>, size: Int, color: Int): List<Set<Int>> {
        val seen = BooleanArray(board.size)
        return buildList {
            for (i in board.indices) if (board[i] == 0 && !seen[i]) {
                val region = HashSet<Int>()
                val borders = HashSet<Int>()
                val stack = ArrayDeque<Int>()
                stack.add(i)
                seen[i] = true
                while (stack.isNotEmpty()) {
                    val point = stack.removeLast()
                    region.add(point)
                    for (n in around(size, point)) {
                        when (board[n]) {
                            0 -> if (!seen[n]) {
                                seen[n] = true
                                stack.add(n)
                            }
                            else -> borders.add(board[n])
                        }
                    }
                }
                if (borders.size == 1 && color in borders) add(region)
            }
        }
    }

    private fun chain(board: List<Int>, size: Int, start: Int) = flood(board, size, start).first

    private fun flood(board: List<Int>, size: Int, start: Int): Pair<Set<Int>, Int> {
        val color = board[start]
        val stones = LinkedHashSet<Int>()
        val libs = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(start)
        stones.add(start)
        while (stack.isNotEmpty()) {
            val point = stack.removeLast()
            for (n in around(size, point)) {
                when (board[n]) {
                    0 -> libs.add(n)
                    color -> if (stones.add(n)) stack.add(n)
                }
            }
        }
        return stones to libs.size
    }

    private fun liberties(board: List<Int>, size: Int, stones: Set<Int>): Set<Int> {
        val libs = HashSet<Int>()
        for (stone in stones) for (n in around(size, stone)) if (board[n] == 0) libs.add(n)
        return libs
    }

    private fun neighborsOf(board: List<Int>, size: Int, region: Set<Int>): List<Int> =
        region.flatMap { around(size, it) }.filter { board[it] != 0 }

    private fun around(size: Int, point: Int): List<Int> {
        val row = point / size
        val col = point % size
        return listOfNotNull(
            if (row > 0) point - size else null,
            if (row < size - 1) point + size else null,
            if (col > 0) point - 1 else null,
            if (col < size - 1) point + 1 else null,
        )
    }

    private fun diagonals(size: Int, point: Int): List<Int> {
        val row = point / size
        val col = point % size
        return listOfNotNull(
            if (row > 0 && col > 0) point - size - 1 else null,
            if (row > 0 && col < size - 1) point - size + 1 else null,
            if (row < size - 1 && col > 0) point + size - 1 else null,
            if (row < size - 1 && col < size - 1) point + size + 1 else null,
        )
    }
}
