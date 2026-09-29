# English lexicon

Read-only SQLite lookup for games: a casefolded lemma returns dictionary senses
(part of speech, definition, and one example sentence when one is available).
The Word Meaning round bank (`meaning/en.tsv`, built by
`tools/meaning/build_meaning_bank.py`) is a separate reviewed set and is not
replaced. MiniLM / ONNX judging is unchanged.

## Free and open sources only

Every shipped row comes from a free, redistributable source. No proprietary
dictionary, no commercial API, and no dump whose licence is unclear. In
particular this lexicon does not use Merriam-Webster, Oxford, Wordnik, Urban
Dictionary, Collins, or similar services.

Shipped sources, also listed under Settings → Credits and licenses:

| Role | Source | Licence |
|---|---|---|
| Playable membership | ENABLE (`enable1.txt`, filtered into `all.txt.gz`) | Public domain |
| Definitions and sense structure | Open English WordNet 2025, core edition | CC BY 4.0, from Princeton WordNet |
| Examples | That synset's OEWN example, else one Tatoeba English CC0 sentence | CC BY 4.0, or CC0 |

Lemmas join on the casefolded form (`ice_cream` and `Ice Cream` are `ice cream`).
A sense is an OEWN synset id plus its definition. An OEWN example for that
synset is preferred, especially one that contains this lemma. Tatoeba is only
the gap fill when that synset has no example, and it is a loose lemma match,
not a checked sense. At most one short example is stored per sense.

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

## Hugging Face

Version 1 does not download from Hugging Face. The builder keeps the canonical
files below. A mirror is not a substitute for those downloads.

Prefer the official file, not a Hub copy:

- **Open English WordNet**: the 2025 core XML from en-word.net. Hub copies such
  as `marksverdhei/wordnet-definitions-en-2021` are older subsets (about 44,000
  rows that have both a definition and an example), not the 2025 core.
- **Tatoeba**: the official English CC0 export
  (`eng_sentences_CC0.tsv.bz2`). `Helsinki-NLP/tatoeba` is a set of parallel
  sentence pairs under CC BY, not that CC0 English file.

Optional later, and not a reason to hold version 1:

- **`mjbommar/opengloss-dictionary`** (CC BY 4.0): about 150,000 lexemes and
  537,000 senses, with synthetic encyclopedic definitions. Quality may be
  uneven. Worth a look only if an OEWN gap needs a richer gloss and the extra
  size is acceptable.
- **`nandhakumarms/qualc-wordnet-en`**: WordNet exported through NLTK, about
  207,000 sense rows. It repeats OEWN and is not a second dictionary.

Do not pack these into the APK:

- **`cstr/en-wiktionary-sqlite-all`** and **`jake-anto/wiktionary`**: CC BY-SA,
  and huge (more than a million entries). Same share-alike and size reasons as
  the Wiktionary note below.

## Considered and not shipped

Version 1 does not download these. Definitions stay OEWN-only.

- **Webster 1913 (public domain) or GCIDE 0.54 (GPL-3.0-or-later)** are the only
  later definition sources to consider, and only for a lemma OEWN does not
  define. GCIDE is Webster 1913 plus later edits; `gcide-0.54.tar.gz` is about
  18 MB compressed. The prose is marked-up and often archaic, so it is not part
  of v1. Modern Merriam-Webster is a different, proprietary work and is not a
  substitute for the 1913 text.
- **Princeton WordNet Gloss Corpus**: free under the WordNet licence, but it
  sense-tags glosses and examples OEWN already contains. It does not add the
  missing sentences, so it is not an easy gap fill.
- **SemCor**: sense-tagged sentences, but the text is the Brown Corpus, which is
  not free to redistribute. It is out, not a phase-2 option.
- **Tatoeba full English export** (CC BY 2.0 FR): free, with more sentences than
  the CC0 file, and each sentence would need attribution. Left as an explicit
  opt-in.
- **Wiktionary via wiktextract**: the tool is MIT; the text is CC BY-SA and GFDL.
  English JSONL is about 22.9 GB raw / 2.6 GB gzip. Share-alike and size keep it
  out. It is not the approved stand-in for a missing OEWN definition.
- **FreeDict**: free bilingual TEI dictionaries (licence per file, often GPL).
  Not an English defining dictionary.
- **FrameNet**: annotated sentences exist, but they are tied to frames rather
  than OEWN synsets. Release 1.5 was non-commercial; confirm any later grant
  before treating it as free. Not used here.
- **OEWN 2025+**: extra proper nouns the letter games do not need.
- **Runtime LLM examples**, Needle 3 / MiniLM changes, and edits to the Word
  Meaning bank.
