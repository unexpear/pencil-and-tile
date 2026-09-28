"""Builds the read-only English lexicon used for word lookup.

usage:
  python tools/lexicon/build_lexicon_db.py --out path/to/lexicon.db

Downloads, unless paths are passed in:
  Open English WordNet 2025 (core edition, CC BY 4.0 + WordNet licence)
  ENABLE enable1.txt (public domain)
  Tatoeba English sentences released under CC0

Free and open sources only. Playable membership is ENABLE. Definitions and
sense ids are Open English WordNet and nothing else. Examples are the OEWN
synset example when it has one, otherwise one short Tatoeba CC0 sentence.
No proprietary dictionary or API is read.

The playable flag is the shipped letter-game list (all.txt.gz), which
tools/words/build_dictionary.py builds from ENABLE. Lemmas are casefolded so
letter games and definitions share the same key. Word Meaning's reviewed bank
(meaning/en.tsv) is not read or written.

See tools/lexicon/DESIGN.md for the schema, example rules, and sources that
were considered and left out.
"""
import argparse
import bz2
import gzip
import hashlib
import os
import re
import sqlite3
import sys
import urllib.request
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CACHE = os.path.join(HERE, "cache")
PLAYABLE_DEFAULT = os.path.join(ROOT, "sudoku-engine", "src", "main", "resources", "words", "all.txt.gz")
BLOCKED_DEFAULT = os.path.join(ROOT, "tools", "words", "blocked.txt")

OEWN_URL = "https://en-word.net/static/english-wordnet-2025.xml.gz"
ENABLE_URL = "https://norvig.com/ngrams/enable1.txt"
TATOEBA_URL = "https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences_CC0.tsv.bz2"

# Same part-of-speech names Word Meaning shows.
POS = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}

# Function words are poor Tatoeba targets: almost every sentence contains them, and the
# shortest hit rarely illustrates the dictionary sense. OEWN examples are still kept.
STOP = frozenset("""
a an the of to and or in on for with from by at as is be it its this that these those
was were are am been being not no nor but if so than then too very can will just
""".split())

# tools/words/blocked.txt's inflection rule also matches a handful of unrelated OEWN lemmas
# (ass+es = assess, butt+er = butter, spic+y = spicy, tit+er = titer, ...). Letter games
# still omit them. The lexicon keeps the dictionary entry and marks them not playable.
KEEP = frozenset({
    "assess", "butter", "butty", "cocker", "dicker", "heller", "pricker", "pricking", "spicy", "titer",
})

SINGLE = re.compile(r"'?[a-z]+(?:[-'][a-z]+)*\Z")
TOKEN = re.compile(r"[A-Za-z]+(?:[-'][A-Za-z]+)*")
USER_AGENT = "pencil-and-tile-lexicon-builder"


def is_blocked(word, bad):
    """Same inflection check as tools/words/build_dictionary.py."""
    if word in bad:
        return True
    for suffix in ("s", "es", "ed", "ing", "er", "ers", "y", "ies"):
        if word.endswith(suffix) and word[:-len(suffix)] in bad:
            return True
    return False


def excluded(lemma, bad):
    if lemma in KEEP:
        return False
    if is_blocked(lemma, bad):
        return True
    for part in re.split(r"[\s-]+", lemma):
        part = part.strip("'")
        if part and part not in KEEP and is_blocked(part, bad):
            return True
    return False


def load_blocked(path):
    words = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip().lower()
            if line and not line.startswith("#"):
                words.add(line)
    return words


def load_playable(path):
    with gzip.open(path, "rt", encoding="ascii") as f:
        return {line.strip().lower() for line in f if line.strip()}


def load_enable(path):
    with open(path, encoding="ascii") as f:
        return {line.strip().lower() for line in f if line.strip()}


def normalize_lemma(written):
    text = written.replace("_", " ").replace("\u2019", "'").replace("\u2018", "'")
    return re.sub(r"\s+", " ", text).strip().lower()


def keep_oewn_lemma(original, lemma, playable, bad):
    """Single words and ordinary phrases. Proper-name entries only when the letter games already play them."""
    if not lemma or len(lemma) > 80 or excluded(lemma, bad):
        return False
    has_upper = any(ch.isupper() for ch in original)
    if " " in lemma:
        parts = lemma.split(" ")
        if has_upper or not (2 <= len(parts) <= 6):
            return False
        return all(SINGLE.fullmatch(part) for part in parts)
    if not SINGLE.fullmatch(lemma):
        return False
    if has_upper and lemma not in playable:
        return False
    return True


def tokens(text):
    return TOKEN.findall(text)


def contains_lemma(sentence, lemma):
    """Whole-word match. A letter after the lemma does not count, so "dog" misses "dogma".
    A leading apostrophe is part of the lemma, so "'hood" misses "hood". "dog's" does count."""
    parts = [part for part in lemma.lower().split(" ") if part]
    if not parts:
        return False
    pattern = r"(?<![A-Za-z])" + r"\s+".join(re.escape(part) for part in parts) + r"(?![A-Za-z])"
    return re.search(pattern, sentence.lower()) is not None


def index_key(token):
    return re.sub(r"[^A-Za-z]", "", token).lower()


def clean_text(text):
    return re.sub(r"\s+", " ", text or "").strip()


def acceptable_sentence(text, bad):
    """Short, ordinary Tatoeba sentence: one clause, no markup, no blocked words."""
    text = clean_text(text)
    if not text or not (12 <= len(text) <= 180):
        return False
    if text[-1] not in ".!?":
        return False
    if any(ch in text for ch in "<>{}[]@#|\\"):
        return False
    lowered = text.lower()
    if "http" in lowered or "www." in lowered:
        return False
    if not any(ch.islower() for ch in text):
        return False
    words = tokens(text)
    if not (5 <= len(words) <= 16):
        return False
    letters = [ch for ch in text if ch.isalpha()]
    if not letters or sum(ch.isupper() for ch in letters) / len(letters) > 0.6:
        return False
    if sum(ch.isalpha() or ch.isspace() for ch in text) < len(text) * 0.75:
        return False
    for word in words:
        if excluded(word.lower(), bad):
            return False
    return True


def pick_oewn_example(lemma, examples):
    """Prefer a short synset example that actually uses this lemma; otherwise the shortest one."""
    cleaned = []
    for example in examples:
        text = clean_text(example)
        if text and len(text) <= 400:
            cleaned.append(text)
    if not cleaned:
        return None
    containing = [example for example in cleaned if contains_lemma(example, lemma)]
    pool = containing or cleaned
    return min(pool, key=lambda example: (len(tokens(example)), len(example)))


def index_sentences(rows, bad):
    """token -> list of (word_count, char_length, sentence_id, text) for acceptable sentences."""
    index = {}
    kept = 0
    for sentence_id, text in rows:
        text = clean_text(text)
        if not acceptable_sentence(text, bad):
            continue
        kept += 1
        words = tokens(text)
        seen = set()
        item = (len(words), len(text), sentence_id, text)
        for word in words:
            key = index_key(word)
            if not key or key in seen or key in STOP or len(key) < 3:
                continue
            seen.add(key)
            index.setdefault(key, []).append(item)
    return index, kept


def pick_tatoeba(lemma, index):
    content = []
    for part in lemma.lower().split(" "):
        key = index_key(part)
        if key and key not in STOP and len(key) >= 3:
            content.append(key)
    if not content:
        return None
    lists = [index.get(key) for key in content]
    if any(not items for items in lists):
        return None
    rare = min(lists, key=len)
    found = [item for item in rare if contains_lemma(item[3], lemma)]
    if not found:
        return None
    return min(found)[3]


def download(url, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".partial"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    print(f"downloading {url}", flush=True)
    with urllib.request.urlopen(req, timeout=120) as response, open(tmp, "wb") as out:
        while True:
            chunk = response.read(1 << 16)
            if not chunk:
                break
            out.write(chunk)
    os.replace(tmp, dest)


def cached(name, url, refresh):
    path = os.path.join(CACHE, name)
    if refresh and os.path.exists(path):
        os.remove(path)
    if not os.path.isfile(path) or os.path.getsize(path) < 1000:
        last = None
        for _ in range(3):
            try:
                download(url, path)
                break
            except Exception as error:  # noqa: BLE001 - retry either network failure
                last = error
                print(f"  retry after {error}", flush=True)
        else:
            raise SystemExit(f"could not download {url}: {last}")
    return path


def parse_oewn(path):
    synsets = {}
    entries = []
    with gzip.open(path, "rb") as stream:
        for _, elem in ET.iterparse(stream, events=("end",)):
            if elem.tag == "LexicalEntry":
                lemma = elem.find("Lemma")
                entries.append((
                    lemma.get("writtenForm"),
                    lemma.get("partOfSpeech"),
                    [sense.get("synset") for sense in elem.findall("Sense")],
                ))
                elem.clear()
            elif elem.tag == "Synset":
                examples = [clean_text(node.text) for node in elem.findall("Example") if clean_text(node.text)]
                synsets[elem.get("id")] = (clean_text(elem.findtext("Definition")), examples)
                elem.clear()
    return entries, synsets


def parse_tatoeba(path):
    rows = []
    with bz2.open(path, "rt", encoding="utf-8") as stream:
        for line in stream:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3 or parts[1] != "eng":
                continue
            rows.append((int(parts[0]), parts[2]))
    return rows


def check_enable(enable, playable, bad):
    filtered = {word for word in enable if word.isalpha() and 2 <= len(word) <= 15 and not is_blocked(word, bad)}
    overlap = len(filtered & playable)
    if not playable or overlap / len(playable) < 0.999:
        raise SystemExit(f"ENABLE does not match the shipped playable list ({overlap}/{len(playable)})")
    only_shipped = sorted(playable - filtered)
    only_enable = sorted(filtered - playable)
    print(f"ENABLE check: {overlap}/{len(playable)} playable words covered; "
          f"{len(only_shipped)} only in the shipped list; {len(only_enable)} only in this ENABLE file", flush=True)
    if len(only_enable) > 50:
        raise SystemExit("ENABLE gained too many words the shipped list does not have; rebuild all.txt.gz first")
    return filtered


def build(oewn_path, enable_path, tatoeba_path, playable_path, blocked_path, out_path):
    bad = load_blocked(blocked_path)
    playable = load_playable(playable_path)
    enable = load_enable(enable_path)
    check_enable(enable, playable, bad)
    print("parsing Open English WordNet", flush=True)
    entries, synsets = parse_oewn(oewn_path)
    print(f"  {len(entries)} lexical entries, {len(synsets)} synsets", flush=True)
    print("indexing Tatoeba CC0 sentences", flush=True)
    tatoeba_index, tatoeba_kept = index_sentences(parse_tatoeba(tatoeba_path), bad)
    print(f"  {tatoeba_kept} short sentences indexed", flush=True)

    # lemma -> list of (pos, synset id, definition, oewn example or None) in file order
    senses = {}
    seen = set()
    skipped_blocked = 0
    for written, pos, synset_ids in entries:
        name = POS.get(pos)
        if not name:
            continue
        lemma = normalize_lemma(written)
        if not keep_oewn_lemma(written, lemma, playable, bad):
            if lemma and excluded(lemma, bad):
                skipped_blocked += 1
            continue
        bucket = senses.setdefault(lemma, [])
        for synset_id in synset_ids:
            key = (lemma, synset_id)
            if key in seen:
                continue
            definition, examples = synsets.get(synset_id, ("", []))
            if not definition:
                continue
            seen.add(key)
            bucket.append((name, synset_id, definition, pick_oewn_example(lemma, examples)))

    lemmas = set(playable) | set(senses)
    for lemma in list(lemmas):
        if excluded(lemma, bad):
            lemmas.discard(lemma)
            senses.pop(lemma, None)
    # Playable words are already filtered; KEEP words are not playable unless the shipped list says so.
    lemmas |= {word for word in playable if not excluded(word, bad)}
    dropped = sorted(word for word in playable if word not in lemmas)
    if dropped:
        raise SystemExit(f"{len(dropped)} playable words were dropped ({', '.join(dropped[:8])}); check blocked.txt")

    tatoeba_for = {}
    for lemma, rows in senses.items():
        if any(example is None for *_, example in rows):
            sentence = pick_tatoeba(lemma, tatoeba_index)
            if sentence:
                tatoeba_for[lemma] = sentence

    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or ".", exist_ok=True)
    partial = out_path + ".partial"
    if os.path.exists(partial):
        os.remove(partial)
    con = sqlite3.connect(partial)
    try:
        con.execute("PRAGMA page_size = 4096")
        con.execute("PRAGMA journal_mode = OFF")
        con.execute("PRAGMA synchronous = OFF")
        con.execute("PRAGMA user_version = 1")
        con.executescript("""
            CREATE TABLE words (
              id INTEGER PRIMARY KEY,
              lemma TEXT NOT NULL,
              playable INTEGER NOT NULL CHECK (playable IN (0, 1))
            );
            CREATE TABLE senses (
              id INTEGER PRIMARY KEY,
              word_id INTEGER NOT NULL REFERENCES words(id),
              pos TEXT NOT NULL,
              definition TEXT NOT NULL,
              oewn_synset_id TEXT
            );
            CREATE TABLE examples (
              sense_id INTEGER NOT NULL REFERENCES senses(id),
              sentence TEXT NOT NULL,
              source TEXT NOT NULL
            );
            CREATE TABLE meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );
        """)
        ordered = sorted(lemmas)
        con.executemany(
            "INSERT INTO words(lemma, playable) VALUES (?, ?)",
            ((lemma, 1 if lemma in playable else 0) for lemma in ordered),
        )
        word_id = {lemma: i + 1 for i, lemma in enumerate(ordered)}
        sense_rows = []
        example_rows = []
        sense_id = 1
        oewn_examples = tatoeba_examples = missing_examples = 0
        for lemma in ordered:
            fallback = tatoeba_for.get(lemma)
            for pos, synset_id, definition, oewn_example in senses.get(lemma, ()):
                sense_rows.append((sense_id, word_id[lemma], pos, definition, synset_id))
                if oewn_example:
                    example_rows.append((sense_id, oewn_example, "oewn"))
                    oewn_examples += 1
                elif fallback:
                    example_rows.append((sense_id, fallback, "tatoeba_cc0"))
                    tatoeba_examples += 1
                else:
                    missing_examples += 1
                sense_id += 1
        con.executemany("INSERT INTO senses(id, word_id, pos, definition, oewn_synset_id) VALUES (?, ?, ?, ?, ?)", sense_rows)
        con.executemany("INSERT INTO examples(sense_id, sentence, source) VALUES (?, ?, ?)", example_rows)
        con.executescript("""
            CREATE UNIQUE INDEX idx_words_lemma ON words(lemma);
            CREATE INDEX idx_senses_word_id ON senses(word_id);
            CREATE UNIQUE INDEX idx_examples_sense_id ON examples(sense_id);
        """)
        meta = {
            "schema": "1",
            "oewn": "Open English WordNet 2025 (core)",
            "oewn_url": OEWN_URL,
            "oewn_license": "CC BY 4.0 and the Princeton WordNet licence",
            "enable": "ENABLE public-domain word list (enable1)",
            "enable_url": ENABLE_URL,
            "playable": "sudoku-engine/src/main/resources/words/all.txt.gz",
            "tatoeba": "Tatoeba English sentences, CC0 subset",
            "tatoeba_url": TATOEBA_URL,
            "tatoeba_license": "CC0 1.0",
            "examples": "OEWN synset example when present; otherwise one short Tatoeba CC0 sentence for the lemma",
            "blocked": "tools/words/blocked.txt with the letter-game inflection rule; KEEP lists suffix false positives",
        }
        con.executemany("INSERT INTO meta(key, value) VALUES (?, ?)", meta.items())
        con.commit()
        con.execute("VACUUM")
        con.commit()
    finally:
        con.close()
    os.replace(partial, out_path)
    digest = hashlib.sha256(open(out_path, "rb").read()).hexdigest()
    sha_path = os.path.splitext(out_path)[0] + ".sha256"
    with open(sha_path, "w", encoding="ascii", newline="\n") as f:
        f.write(digest + "\n")
    db_bytes = os.path.getsize(out_path)
    with open(out_path, "rb") as f:
        gzip_bytes = len(gzip.compress(f.read(), compresslevel=6))
    stats = {
        "words": len(ordered),
        "playable": sum(1 for lemma in ordered if lemma in playable),
        "senses": len(sense_rows),
        "examples_oewn": oewn_examples,
        "examples_tatoeba_cc0": tatoeba_examples,
        "senses_without_example": missing_examples,
        "skipped_blocked_entries": skipped_blocked,
        "tatoeba_sentences_indexed": tatoeba_kept,
        "db_bytes": db_bytes,
        "gzip6_bytes": gzip_bytes,
        "sha256": digest,
    }
    stats_path = os.path.splitext(out_path)[0] + ".stats.txt"
    with open(stats_path, "w", encoding="utf-8", newline="\n") as f:
        for key, value in stats.items():
            f.write(f"{key}\t{value}\n")
    print(f"words {stats['words']} ({stats['playable']} playable), senses {stats['senses']}", flush=True)
    print(f"examples oewn {oewn_examples}, tatoeba_cc0 {tatoeba_examples}, none {missing_examples}", flush=True)
    print(f"db {db_bytes} bytes, gzip-6 {gzip_bytes} bytes", flush=True)
    print(f"wrote {out_path}", flush=True)
    return stats


def main(argv=None):
    parser = argparse.ArgumentParser(description="Build the English lexicon SQLite database.")
    parser.add_argument("--out", required=True, help="path of lexicon.db to write")
    parser.add_argument("--oewn", help="english-wordnet-2025.xml.gz (downloaded if omitted)")
    parser.add_argument("--enable", help="enable1.txt (downloaded if omitted)")
    parser.add_argument("--tatoeba", help="eng_sentences_CC0.tsv.bz2 (downloaded if omitted)")
    parser.add_argument("--playable", default=PLAYABLE_DEFAULT, help="gzipped shipped word list (all.txt.gz)")
    parser.add_argument("--blocked", default=BLOCKED_DEFAULT)
    parser.add_argument("--refresh", action="store_true", help="download the sources again")
    args = parser.parse_args(argv)
    oewn = args.oewn or cached("english-wordnet-2025.xml.gz", OEWN_URL, args.refresh)
    enable = args.enable or cached("enable1.txt", ENABLE_URL, args.refresh)
    tatoeba = args.tatoeba or cached("eng_sentences_CC0.tsv.bz2", TATOEBA_URL, args.refresh)
    for path in (oewn, enable, tatoeba, args.playable, args.blocked):
        if not os.path.isfile(path):
            raise SystemExit(f"missing {path}")
    with open(oewn, "rb") as f:
        if f.read(2) != b"\x1f\x8b":
            raise SystemExit(f"{oewn} is not a gzip file")
    build(oewn, enable, tatoeba, args.playable, args.blocked, args.out)


if __name__ == "__main__":
    main()
