"""Adds Word Meaning words whose sentences come from the lexicon grab.

The reviewed bank in meaning/en.tsv is left alone. This writes meaning/open.tsv:
an Open English WordNet example when that example is a full sentence using the
word, otherwise one Tatoeba sentence for a word that has only one meaning.
Phrase lists are built the same way as tools/meaning/build_meaning_bank.py.

usage: python tools/meaning/build_open_bank.py
"""
import gzip
import os
import re
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(ROOT, "tools", "lexicon"))
sys.path.insert(0, HERE)
import build_lexicon_db as lex
import build_meaning_bank as bank

DB = os.path.join(ROOT, "sudoku-engine", "build", "lexicon", "lexicon.db")
WORDNET = os.path.join(ROOT, "tools", "lexicon", "cache", "english-wordnet-2025.xml.gz")
OUT = os.path.join(ROOT, "sudoku-engine", "src", "main", "resources", "meaning", "open.tsv")
REVIEWED = os.path.join(ROOT, "sudoku-engine", "src", "main", "resources", "meaning", "en.tsv")
COMMON = os.path.join(ROOT, "sudoku-engine", "src", "main", "resources", "words", "common.txt.gz")
WORD = re.compile(r"[A-Za-z]+")


def reviewed_words():
    words = set()
    for line in open(REVIEWED, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        words.add(line.split("\t")[0])
    return words


def common_words():
    with gzip.open(COMMON, "rt", encoding="ascii") as f:
        return {line.strip().lower() for line in f if line.strip()}


def usable(sentence, lemma, bad):
    sentence = lex.clean_text(sentence)
    if not sentence or sentence[-1:] not in ".!?" or "\t" in sentence or "\n" in sentence:
        return None
    if any(ch.isdigit() for ch in sentence) or "(" in sentence or ")" in sentence:
        return None
    count = len(WORD.findall(sentence))
    if not 12 <= count <= 20:
        return None
    try:
        bank.validate_context(lemma, sentence)
    except ValueError:
        return None
    if any(lex.excluded(word.lower(), bad) for word in lex.tokens(sentence)):
        return None
    return sentence[0].upper() + sentence[1:]


def phrases(word, pos, sense, syn, entries, synsets, sense_by_id):
    definition = syn["definition"]
    right = list(syn["members"])
    close, wrong = [], []
    for rel, target in syn["rel"]:
        other = synsets.get(target)
        if not other:
            continue
        if rel in ("similar", "also"):
            right += other["members"]
        elif rel in ("hypernym", "instance_hypernym"):
            close += other["members"]
    for rel in sense.findall("SenseRelation"):
        if rel.get("relType") == "antonym" and rel.get("target") in sense_by_id:
            lemma, sid = sense_by_id[rel.get("target")]
            wrong += [lemma] + synsets[sid]["members"]
    if pos == "s":
        wrong += bank.antonyms_of_head(syn, synsets, sense_by_id, entries)
    right += [re.sub(r"\([^)]*\)", " ", part).strip() for part in definition.split(";")]
    for _pos, senses in entries.get(word.lower(), []):
        for other in senses:
            if other is sense:
                continue
            gloss = synsets[other.get("synset")]["definition"]
            if gloss:
                close.append(gloss.split(";")[0].strip())
    def readable(items):
        return [item for item in items if re.search(r"[A-Za-z]", item)]

    right = readable(bank.clean(right, word))
    wrong = [item for item in readable(bank.clean(wrong, word)) if item.lower() not in {r.lower() for r in right}]
    close = [item for item in readable(bank.clean(close, word)) if item.lower() not in {r.lower() for r in right} | {w.lower() for w in wrong}][:12]
    return definition, right, wrong, close


def main():
    skip = reviewed_words()
    everyday = common_words()
    bad = lex.load_blocked(os.path.join(ROOT, "tools", "words", "blocked.txt"))
    print("loading WordNet", flush=True)
    entries, synsets, sense_by_id = bank.load(WORDNET)
    by_synset = {}
    for word, groups in entries.items():
        for pos, senses in groups:
            for sense in senses:
                by_synset.setdefault(sense.get("synset"), []).append((word, pos, sense))
    con = sqlite3.connect(DB)
    rows = con.execute(
        """
        SELECT w.lemma, s.pos, s.oewn_synset_id, e.sentence, e.source,
               (SELECT COUNT(*) FROM senses s2 WHERE s2.word_id = w.id)
        FROM words w
        JOIN senses s ON s.word_id = w.id
        JOIN examples e ON e.sense_id = s.id
        WHERE w.playable = 1
        """
    ).fetchall()
    best = {}
    for lemma, pos_name, synset_id, sentence, source, sense_count in rows:
        if lemma in skip or not lemma.isalpha() or " " in lemma:
            continue
        if source == "tatoeba_cc0" and sense_count != 1:
            continue
        sentence = usable(sentence, lemma, bad)
        if sentence is None:
            continue
        key = (len(WORD.findall(sentence)), lemma)
        previous = best.get(lemma)
        if previous is None or key < previous[0]:
            best[lemma] = (key, pos_name, synset_id, sentence, source)
    written = []
    for lemma, (_key, pos_name, synset_id, sentence, source) in sorted(best.items()):
        found = None
        for word, pos, sense in by_synset.get(synset_id, []):
            if word.lower() == lemma and bank.KIND.get(pos) == pos_name:
                found = (pos, sense)
                break
        if found is None:
            continue
        pos, sense = found
        syn = synsets[synset_id]
        definition, right, wrong, close = phrases(lemma, pos, sense, syn, entries, synsets, sense_by_id)
        if not right or "\t" in definition or "\n" in definition:
            continue
        level = "1" if lemma in everyday else "2"
        kind = bank.KIND.get(pos, "word")
        row = [lemma, kind, level, sentence, definition, "; ".join(right), "; ".join(close), "; ".join(wrong)]
        if any("\t" in part or "\n" in part for part in row):
            continue
        written.append(row)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Extra Word Meaning words. Sentences are the lexicon's Open English WordNet example,\n")
        f.write("# or one Tatoeba CC0 sentence when the word has a single meaning. The reviewed bank is unchanged.\n")
        f.write("# word\tkind\tlevel\tsentence\tdefinition\tright\tclose\twrong\n")
        for row in written:
            f.write("\t".join(row) + "\n")
    print(f"{len(written)} words -> {OUT}")
    print("level 1", sum(1 for row in written if row[2] == "1"), "level 2", sum(1 for row in written if row[2] == "2"))
    for row in written[:8]:
        print("-", row[0], row[3][:110])


if __name__ == "__main__":
    main()
