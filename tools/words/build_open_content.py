"""Builds the extra English lists the word games draw from.

usage: python tools/words/build_open_content.py

Reads the cached Open English WordNet, ENABLE playable list, everyday list and
Tatoeba CC0 sentences (the same files tools/lexicon/build_lexicon_db.py downloads)
and writes, under sudoku-engine/src/main/resources/words/:

  lemmas.txt.gz   WordNet lemmas that letter games already accept
  clues.tsv.gz    one short WordNet definition per everyday word, with the word itself removed
  sayings.txt.gz  Tatoeba CC0 sentences short enough for a cryptogram or dropquote

Words in blocked.txt are left out. Word Meaning's reviewed bank is not touched.
"""
import bz2
import gzip
import os
import re
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CACHE = os.path.join(ROOT, "tools", "lexicon", "cache")
WORDS = os.path.join(ROOT, "sudoku-engine", "src", "main", "resources", "words")
WORDNET = os.path.join(CACHE, "english-wordnet-2025.xml.gz")
TATOEBA = os.path.join(CACHE, "eng_sentences_CC0.tsv.bz2")


def load_gzip_words(name):
    path = os.path.join(WORDS, name)
    with gzip.open(path, "rt", encoding="ascii") as f:
        return {line.strip() for line in f if line.strip()}


def blocked():
    words = set()
    for line in open(os.path.join(HERE, "blocked.txt"), encoding="utf-8"):
        line = line.strip().lower()
        if line and not line.startswith("#"):
            words.add(line)
    return words


def blocked_in(text, bad):
    for word in re.findall(r"[a-z']+", text.lower()):
        if word in bad:
            return True
    return False


def clean_definition(text, word):
    text = (text or "").replace("’", "'").replace("“", '"').replace("”", '"')
    text = re.sub(r"\([^)]*\)", " ", text)
    text = text.split(";")[0]
    text = re.sub(r"\s+", " ", text).strip(" .")
    if not text or any(ord(ch) > 126 for ch in text):
        return None
    if re.search(r"\b" + re.escape(word) + r"\b", text, re.I):
        return None
    if not (12 <= len(text) <= 110) or not text[0].isalpha():
        return None
    if len(re.findall(r"[A-Za-z]+", text)) < 3:
        return None
    return text[0].upper() + text[1:]


def build_lexicon(playable, everyday, bad):
    lemmas = set()
    clues = {}
    root = ET.parse(gzip.open(WORDNET)).getroot()
    lex = root.find("Lexicon")
    members = {}
    for entry in lex.findall("LexicalEntry"):
        written = entry.find("Lemma").get("writtenForm")
        if not (written.isalpha() and written.islower()):
            continue
        upper = written.upper()
        if upper in playable and written not in bad:
            lemmas.add(upper)
        for sense in entry.findall("Sense"):
            members.setdefault(sense.get("synset"), []).append(written)
    for synset in lex.findall("Synset"):
        definition = synset.findtext("Definition") or ""
        for word in members.get(synset.get("id"), []):
            if word not in everyday or not word.isalpha() or not 3 <= len(word) <= 10:
                continue
            clue = clean_definition(definition, word)
            if clue is None or word in bad:
                continue
            upper = word.upper()
            previous = clues.get(upper)
            if previous is None or len(clue) < len(previous):
                clues[upper] = clue
    return lemmas, clues


# Tatoeba's English lessons lean on the same first names. A cryptogram of "Tom did this" is a weak saying.
STOCK_NAMES = {
    "tom", "mary", "john", "alice", "mike", "sam", "bob", "bill", "jane", "sue",
    "jack", "jim", "joe", "peter", "paul", "david", "sarah", "lisa", "kate",
}


def usable_saying(text, bad, classics):
    text = text.strip().replace("’", "'").replace("“", '"').replace("”", '"')
    text = text.replace("—", "-").replace("–", "-")
    if text.casefold() in classics:
        return None
    if any(ch.isdigit() for ch in text) or any(ord(ch) > 126 for ch in text):
        return None
    if any(ch in text for ch in "@/<>[]{}\\|"):
        return None
    if not (20 <= len(text) <= 180) or text[-1:] not in ".!?":
        return None
    words = re.findall(r"[A-Za-z]+(?:'[A-Za-z]+)?", text)
    if not (4 <= len(words) <= 24) or any(len(word) > 13 for word in words):
        return None
    if any(word.lower() in STOCK_NAMES for word in words):
        return None
    # A capital after the first word is a name or a place, not a plain saying. Keep "I".
    if any(word[:1].isupper() and word != "I" for word in words[1:]):
        return None
    letters = sum(ch.isalpha() for ch in text)
    if not (12 <= letters <= 160) or blocked_in(text, bad):
        return None
    return text


def build_sayings(bad, classics):
    kept = []
    seen = set()
    with bz2.open(TATOEBA, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            text = usable_saying(parts[2], bad, classics)
            if text is None or text in seen:
                continue
            seen.add(text)
            kept.append(text)
    return kept


def write_gzip(name, lines):
    path = os.path.join(WORDS, name)
    with gzip.open(path, "wt", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{name}: {len(lines)}")


def main():
    playable = load_gzip_words("all.txt.gz")
    everyday = {word.lower() for word in load_gzip_words("common.txt.gz")}
    bad = blocked()
    lemmas, clues = build_lexicon(playable, everyday, bad)
    classics = set()
    quotes = os.path.join(ROOT, "sudoku-engine", "src", "main", "kotlin",
                          "com", "simplegamegen", "sudoku", "wordplay", "Quotes.kt")
    for match in re.findall(r'"([^"]+)" to "', open(quotes, encoding="utf-8").read()):
        classics.add(match.casefold())
    sayings = build_sayings(bad, classics)
    write_gzip("lemmas.txt.gz", sorted(lemmas))
    write_gzip("clues.tsv.gz", [f"{word}\t{clue}" for word, clue in sorted(clues.items())])
    write_gzip("sayings.txt.gz", sayings)
    five = [word for word in lemmas if len(word) == 5]
    print(f"five-letter lemmas: {len(five)} everyday {sum(1 for w in five if w.lower() in everyday)}")
    if len(lemmas) < 5000 or len(clues) < 2000 or len(sayings) < 1000:
        raise SystemExit("word banks came out smaller than expected")


if __name__ == "__main__":
    main()
