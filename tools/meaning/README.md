# Word Meaning context clues

`contexts_en.tsv` pairs each original sentence with the target word, part of speech,
and exact Open English WordNet definition used by its answer bank. Review these
together: a long sentence is not necessarily a useful clue, and a valid definition
may be the wrong sense for a particular sentence.

Every shipped word has a complete clue of at least 12 words containing the exact
target word. Include an action, consequence, contrast, or other evidence that
helps the player infer the intended meaning. Do not pad dictionary fragments.

The September 2026 repair keeps all 337 word IDs, levels, definitions and answer
phrases unchanged. Existing saved rounds therefore keep their selected words and
scores while showing the improved contexts. Other dictionary senses remain in
the existing close-answer list; they are not automatically fully correct for the
sentence shown. The player's existing “I was right” override is retained.

To regenerate, download the [official 2025 WordNet XML](https://en-word.net/downloads), then run:

```text
python tools/meaning/build_meaning_bank.py path/to/english-wordnet-2025.xml.gz
python -m unittest discover -s tools/meaning -p "test_*.py"
```

The generator refuses a missing or changed reviewed sense rather than selecting
another meaning because it happens to have a dictionary example. New vocabulary
without a reviewed context is reported and skipped. CI checks the source contexts
against the shipped bank, and engine tests reject short prompts and exercise
multiple-meaning grading. WordNet's [format specification](https://globalwordnet.github.io/schemas/)
describes the sense-to-synset links used for this selection.
