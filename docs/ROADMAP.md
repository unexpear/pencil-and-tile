# Roadmap

Work happens top to bottom, one item at a time. Games inspired by other products get their own
names, art and content; mechanics are shared ideas, names and trade dress are not.

## Done recently
- Common Threads (original rule-generated grouping puzzles, proven to have one answer)
- Five Letters (five-letter word guessing, colour-free marks)
- Word Meaning (owner's idea): explain a word from its sentence; definitions and examples from Open English
  WordNet; judged on the device by word checks that understand "not", with a small sentence model
  (all-MiniLM-L6-v2) as tie-breaker and an "I was right" override. Credits screen lists all licences.
- Tutorials checked against the real game rules in tests (Sudoku, Killer, Calcudoku, Kakuro,
  Futoshiki, Hitori, Nonograms, Five Letters, Common Threads)
- Interface, rules, tutorials and screen-reader text in 中文, 日本語, Español, Deutsch

## Up next
1. **Release 1 and Google Play** as a new app (not inside the existing one). Prepared: name Pencil & Tile,
   ID com.simplegamegen.puzzles, version 1.0.0, release bundle, listing in 5 languages, privacy policy,
   Data safety and rating answers, icon, feature graphic, screenshots, credits screen, closed-test steps and
   tester invite (docs/GOOGLE_PLAY.md). Waiting on the owner: upload key, Play Console setup, 14-day test.
2. **Word content for each language** (es, de, ja, zh): word lists, clues, sayings, Five Letters
   and Common Threads word banks, and per-language word-game tutorials.

## Researched, waiting their turn
- **Word combat deck-builder** (inspired by Paperback Adventures): spell words from letter tiles to
  fight enemies, upgrade tiles between fights. Working names: Wordsworn, Vowelbreaker.
- **Word-shading logic puzzle** (inspired by LOK): spell command words to black out a letter grid.
  Working names: Blotwords, Spellshade.
- **Letter-region crossword** (inspired by Knotwords): place each region's letters so every row and
  column reads as words; generator proves one solution. Working names: Word Quilt, Letter Parcels.
- **Category game** (inspired by Scattergories, whose roots are the public-domain parlour game
  "Categories"): a letter and a list of categories; computer rivals make matching answers score zero.
  Answers checked against curated lists, with the embedding model only for "plausible?" cases.
  Working names: Off the List, Lone Letter.
- **Letter-grid word hunt** (inspired by Boggle): chain neighbouring letters into words; grids come
  from our own letter frequencies and are solved before play so each has enough words. Needs a large
  public-domain dictionary (ENABLE) with a common-word tier. Working names: Kingstep, Letter Sprawl.

## Shared word data
- **Open English WordNet** (CC BY 4.0, from Princeton WordNet) now feeds Word Meaning through
  `tools/meaning/build_meaning_bank.py`. Other games can use it too: Common Threads categories from
  its "is a kind of" trees, crossword clues from definitions, a bigger Five Letters guess list, category-game
  answer lists and letter-grid dictionaries.

## Ideas
- Chess, a honeycomb word builder, Word Ladder.
