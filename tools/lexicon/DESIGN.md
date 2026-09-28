# English lexicon

Read-only SQLite lookup for games: a casefolded lemma returns dictionary senses
(part of speech, definition, and one example sentence when one is available).
The Word Meaning round bank (`meaning/en.tsv`, built by
`tools/meaning/build_meaning_bank.py`) is a separate reviewed set and is not
replaced. MiniLM / ONNX judging is unchanged.

`./gradlew :sudoku-engine:buildLexiconDb` writes `lexicon.db` (and a sha256)
under `sudoku-engine/build/lexicon/`. The app task `copyLexiconDb` packs it as
`assets/lexicon/lexicon.db`. Nothing in that database is committed; the first
build downloads the sources into `tools/lexicon/cache/` (gitignored). Pass
`--oewn`, `--enable`, and `--tatoeba` to the script to build from local files.

## Sources

- **Open English WordNet 2025**, core edition (not 2025+): definitions, synset
  ids, and example sentences. CC BY 4.0, derived from Princeton WordNet under
  the WordNet licence. Same attribution as Word Meaning.
- **ENABLE** (public domain, `enable1.txt`): membership check. The playable
  flag is the shipped letter-game list `all.txt.gz` from
  `tools/words/build_dictionary.py` (ENABLE, 2–15 letters, `blocked.txt`
  removed), so a word the letter games accept is the same key as a lexicon row.
- **Tatoeba English CC0** (`eng_sentences_CC0.tsv.bz2`): one short example for a
  lemma when that sense has no OEWN example.

Proper-name entries (a capital in the OEWN written form) are stored only when
the casefolded lemma is already playable. Ordinary multi-word lemmas such as
"ice cream" are included and are not playable. Lemmas on `blocked.txt`, and
inflections of those lemmas, are omitted, with a short exception list in the
builder for suffix collisions such as "butter" and "assess". Those exceptions
stay non-playable unless the shipped word list already contains them.

## Examples

OEWN examples hang off the synset, so they sometimes use another member of the
synset ("chase" in an example for one sense of "dog"). The builder prefers an
OEWN example that contains the lemma as a whole word, and otherwise keeps the
shortest OEWN example. It does not invent text.

When a sense has no OEWN example, one Tatoeba CC0 sentence is attached to every
such sense of that lemma. The sentence must contain the lemma at a word boundary
(so "dog" matches "dog's" and misses "dogma"; a leading apostrophe is kept, so
"'hood" misses "hood"), be 5–16 words, and look like a normal sentence.
Function words are skipped. The sentence illustrates the word, not a checked
sense. Senses that still have no suitable sentence are stored with a definition
only.

## Schema

`words(id, lemma, playable)`, `senses(id, word_id, pos, definition, oewn_synset_id)`,
`examples(sense_id, sentence, source)` with `source` of `oewn` or `tatoeba_cc0`.
Unique index on the casefolded lemma, index on `word_id`, unique index on
`examples.sense_id`. No full-text search and no synset relation graph. A `meta`
table records the source URLs.

## Not in this version

- **Wiktionary** (English dump or Kaikki): CC BY-SA, so the share-alike terms
  would cover a much larger database, and the dump is far bigger than the
  25–40 MB budget. Left for a later decision, not imported here.
- **Commercial dictionaries** (Oxford, Merriam-Webster, Collins, and similar):
  not licensed for redistribution inside a GPL-3 app.
- **Tatoeba full English export** (CC BY 2.0 FR): more sentences, but every
  sentence then needs attribution. The CC0 subset is enough for a first fill-in;
  the full dump can be an explicit later option.
- **OEWN 2025+**: adds proper nouns the letter games do not use.
- **Runtime LLM examples**, Needle 3 / MiniLM changes, and edits to the Word
  Meaning bank.
