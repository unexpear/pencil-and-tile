package com.simplegamegen.sudoku.tabletop

object GameGuide {
    fun rules(game: TableGame): String = when (game) {
        TableGame.SOLITAIRE -> "Klondike: build foundations from Ace to King by suit. Build columns downward in alternating colors; only Kings fill empty columns. Tap a face-up card to select it and the cards below it, then a destination column or foundation. Stock draws 1 or 3; unlimited redeals. Shuffled deals are not guaranteed winnable. Hints suggest legal moves, not a proven win."
        TableGame.MINES -> "Reveal every safe square. Numbers count mines in the eight surrounding squares. Use Flag mode to mark suspects; tap an open number in Reveal mode to open its unflagged neighbors when the flag count matches. Wrong flags can make this dangerous. The opening and its neighbors are safe. Hints use visible clues only; later guesses may be necessary."
        TableGame.CHECKERS -> "English/American Checkers: you are the dark pieces at the bottom and move first. Men move and capture diagonally forward; Kings also move backward. Captures are mandatory and multiple jumps must finish. Reaching the far row crowns a man and ends its turn. No legal move loses. Threefold repetition or 40 moves each without a capture or man move draws automatically. Tap a piece, then a marked destination."
        TableGame.REVERSI -> "You play Black and move first. Place a disc to trap a line of White discs between two Black discs. All trapped lines flip. If a player has no legal move, that turn is skipped automatically. The game ends when neither can move; most discs wins. Marked squares are legal moves."
        TableGame.DOMINOES -> "Double-six Draw Dominoes, one hand against the computer. Seven tiles each; you lead with any tile. Match a tile to either end of the chain. When no tile fits, draw until one fits or the boneyard is empty, then pass. Empty your hand to win; if both pass, fewer remaining pips wins (equal pips draws). Select a tile, then Left or Right. The computer cannot see your tiles."
    }
    fun settingDescription(game: TableGame, setting: Int): String = when (game) {
        TableGame.SOLITAIRE -> "${game.settings[setting]} · 52 cards · unlimited stock redeals"
        TableGame.MINES -> MinesState.presets[setting].let { "${it.width} × ${it.height} · ${it.mines} mines · safe opening" }
        TableGame.CHECKERS, TableGame.REVERSI -> "${game.settings[setting]} computer · standard 8 × 8 opening"
        TableGame.DOMINOES -> "${game.settings[setting]} computer · 28 tiles · single hand"
    }
}
