# Roadmap

Work happens top to bottom, one item at a time. Games inspired by other products get their own
names, art and content; mechanics are shared ideas, names and trade dress are not.

## Done recently
- **Release 1.0.0 on Google Play** as Pencil & Tile (com.simplegamegen.puzzles): store listing in 5 languages,
  closed-testing track with the Alpha testers list, sent for review on 26 Sep 2026.
- **Blotwords** (inspired by LOK): ink a letter grid by writing invented command words (VUM, DRIF, ZUV, KEL)
  whose effects players find out for themselves; a Discover trail teaches them one puzzle at a time. Goes out in 1.1.0.
- Common Threads (original rule-generated grouping puzzles, proven to have one answer)
- Five Letters (five-letter word guessing, colour-free marks)
- Word Meaning (owner's idea): explain a word from its sentence; definitions and examples from Open English
  WordNet; judged on the device by word checks that understand "not", with a small sentence model
  (all-MiniLM-L6-v2) as tie-breaker and an "I was right" override. Credits screen lists all licences.
- Tutorials checked against the real game rules in tests (Sudoku, Killer, Calcudoku, Kakuro,
  Futoshiki, Hitori, Nonograms, Five Letters, Common Threads)
- Interface, rules, tutorials and screen-reader text in 中文, 日本語, Español, Deutsch

## Up next
1. **Release 1.1.0** with the new games: bump the version, update the store listing's game count and list,
   and set up the same GitHub Actions upload StandardTune uses (service account, `r0adkll/upload-google-play`).
   Closed testing still needs 12 opted-in testers for 14 days before production.
2. **Word content for each language** (es, de, ja, zh): word lists, clues, sayings, Five Letters
   and Common Threads word banks, and per-language word-game tutorials.

## Built for 1.1.0
- **Letter Sprawl** (inspired by Boggle): chain touching letters into words; ENABLE dictionary with an
  everyday tier from WordNet.
- **Wordsworn** (inspired by Paperback Adventures): spell words from letter tiles to fight a run of monsters.
- **Word Quilt** (inspired by Knotwords): place each patch's letters so every run across and down is a word.
- **Lone Letter** (inspired by Scattergories, from the public-domain parlour game "Categories"): one letter,
  many categories, computer rivals whose matching answers score zero. Lists in
  `sudoku-engine/src/main/resources/categories/en.tsv` from `tools/words/build_categories.py`.

## Marketing (free)
- Store tags set: Crossword, Logic puzzle, Mahjong solitaire, Solitaire, Word.
- `docs/store/listing.md` updated for 1.1.0 (37 games, the new word games, "free and open source").
- A "Word games" custom store listing for word-game searches: `docs/store/listing-word-games.md`, to publish
  with 1.1.0.
- Home screen shows base games with their variants underneath (Killer and Samurai under Sudoku, Spider and
  Pyramid under Solitaire); the store still counts every game.

## Support
- A separate support Discord, set up by the owner from `docs/community/DISCORD.md`; its invite link then
  goes in Settings → About, the Play listing and the README.

## Shared word data
- **Open English WordNet** (CC BY 4.0, from Princeton WordNet) now feeds Word Meaning through
  `tools/meaning/build_meaning_bank.py`. Other games can use it too: Common Threads categories from
  its "is a kind of" trees, crossword clues from definitions, a bigger Five Letters guess list, category-game
  answer lists and letter-grid dictionaries.

## Ideas
- Chess, a honeycomb word builder, Word Ladder.
