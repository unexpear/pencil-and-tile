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

## Not in version 1

Version 1 is only OEWN definitions, OEWN examples, and Tatoeba CC0 sentences.
The resources below were checked as ways to fill a missing definition or a
missing example. None of them are downloaded or imported.

### Optional phase 2

- **GCIDE 0.54** (GNU Collaborative International Dictionary of English, GPL-3.0-or-later):
  Webster's 1913 dictionary (public domain) plus later edits. The release tarball
  is about 18 MB compressed (`gcide-0.54.tar.gz`); older notes cite about 130,000
  headwords. GPL text can sit in this GPL-3 app, but the entries are marked-up
  19th-century prose, some of them outdated, and they overlap OEWN. Worth a look
  only for lemmas OEWN does not define, after the same block list.
- **FreeDict**: free bilingual TEI dictionaries, not an English defining dictionary.
  Most are GPL; the licence is in each file's TEI header and some are derived from
  share-alike sources. Useful later for another language's glosses, not for
  English sense gaps.
- **Wiktionary via wiktextract** (Kaikki.org English JSONL): the extractor is MIT,
  the extracted text is Wiktionary's dual CC BY-SA and GFDL. The English file is
  about 22.9 GB uncompressed and 2.6 GB gzip, far past the 25–40 MB budget.
  A slice of English definitions might fit, but share-alike would cover those
  rows and attribution would have to travel with them. Still the largest optional
  source, not a v1 import.
- **Princeton WordNet Gloss Corpus** (tagged glosses): words inside WordNet
  definitions and examples are linked to senses, under the WordNet licence
  (use and redistribution with the Princeton notice). It does not add sentences
  this app does not already have from OEWN. Optional if a later version wants
  sense links inside a gloss, not as a gap-fill for missing examples.
- **SemCor**: Brown Corpus sentences tagged with WordNet senses, which is the
  shape we would want for a sense-specific example. The sentence text is the
  Brown Corpus, whose samples are not cleared for redistribution in a public
  GPL app (scholarly / non-commercial limits). Princeton's SemCor notice does
  not replace that. Do not ship SemCor sentences unless the underlying text is
  separately cleared.
- **FrameNet** (Berkeley / ICSI): on the order of 13,000 lexical units and
  200,000 annotated sentences. NLTK distributes release 1.7 as CC BY 3.0;
  release 1.5 was non-commercial, and the upstream download still goes through
  ICSI, so the current grant has to be read before any sentence is copied.
  Examples are tied to semantic frames, not to OEWN synsets, so they need a
  mapping. Optional after that check, and only for senses that still have no
  OEWN or Tatoeba sentence.
- **Tatoeba full English export** (CC BY 2.0 FR): more sentences than the CC0
  subset, but each sentence then needs attribution. An explicit opt-in, not the
  default fill.

### Still out

- **Commercial dictionaries** (Oxford, Merriam-Webster, Collins, and similar):
  not licensed for redistribution inside a GPL-3 app.
- **OEWN 2025+**: adds proper nouns the letter games do not use.
- **Runtime LLM examples**, Needle 3 / MiniLM changes, and edits to the Word
  Meaning bank.
