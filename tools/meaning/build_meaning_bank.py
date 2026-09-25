"""Builds the Word Meaning bank from Open English WordNet.

usage: python tools/meaning/build_meaning_bank.py path/to/english-wordnet-2025.xml.gz

For each word in words_en.txt it picks the most common dictionary sense (adjectives first, then verbs,
nouns, adverbs) whose example sentence uses the word, and writes one line to
sudoku-engine/src/main/resources/meaning/en.tsv:

    word  kind  level  sentence  definition  right  close  wrong

right = synonyms, similar words and the definition's parts; close = broader words (hypernyms) and the
word's other senses; wrong = antonyms and their synonyms. Extras from curated_en.txt are merged in.
Open English WordNet: CC BY 4.0, derived from Princeton WordNet (WordNet licence); see NOTICE in the app.
"""
import gzip, os, re, sys, xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "sudoku-engine", "src", "main", "resources", "meaning", "en.tsv")
KIND = {"a": "adjective", "s": "adjective", "v": "verb", "n": "noun", "r": "adverb"}
POS_ORDER = ["a", "v", "n", "r"]


def load(path):
    root = ET.parse(gzip.open(path)).getroot()
    lex = root.find("Lexicon")
    entries = {}      # lemma -> list of (pos, [sense elements])
    lemma_of = {}     # entry id -> lemma
    sense_by_id = {}  # sense id -> (lemma, synset id)
    for e in lex.findall("LexicalEntry"):
        lemma = e.find("Lemma").get("writtenForm")
        pos = e.find("Lemma").get("partOfSpeech")
        lemma_of[e.get("id")] = lemma
        senses = e.findall("Sense")
        entries.setdefault(lemma.lower(), []).append((pos, senses))
        for s in senses:
            sense_by_id[s.get("id")] = (lemma, s.get("synset"))
    synsets = {}
    for s in lex.findall("Synset"):
        synsets[s.get("id")] = {
            "members": [lemma_of.get(m, "") for m in s.get("members", "").split()],
            "definition": (s.findtext("Definition") or "").strip(),
            "examples": [x.text or "" for x in s.findall("Example")],
            "rel": [(r.get("relType"), r.get("target")) for r in s.findall("SynsetRelation")],
        }
    return entries, synsets, sense_by_id


def uses(sentence, word):
    """True when the sentence contains the word or an inflection of it."""
    w = word.lower()
    stem = w[:-1] if w.endswith("e") else w
    return re.search(r"\b" + re.escape(stem) + r"[a-z]*\b", sentence.lower()) is not None


def clean(phrases, word):
    out = []
    for p in phrases:
        p = p.replace("_", " ").strip()
        if not p or ";" in p or "\t" in p: continue
        if word.lower() in p.lower(): continue  # "frugally" doesn't explain "frugal"
        if p.lower() not in [o.lower() for o in out]: out.append(p)
    return out


STOP = {"with", "the", "and", "for", "from", "that", "being", "like", "likes", "very", "not", "into", "than", "some", "who", "what",
        "something", "someone", "others", "other", "especially", "able", "have", "having", "has", "are", "was", "more", "much"}


def words_of(text):
    """Word stems (first five letters) without filler, so "enjoying" meets "enjoys"."""
    return {w[:5] for w in re.findall(r"[a-z]+", text.lower()) if len(w) > 2 and w not in STOP}


def pick(word, entries, synsets, curated_sentence, curated_meaning=None, curated_kind=None):
    """The word's most common sense (adjectives first, then verbs, nouns, adverbs). Its own example is used
    when it has one that uses the word, else the curated sentence; only without either does a rarer sense
    with an example stand in."""
    options = entries.get(word.lower(), [])
    rank = lambda o: POS_ORDER.index("a" if o[0] == "s" else o[0]) if ("a" if o[0] == "s" else o[0]) in POS_ORDER else 9
    senses = [(pos, s, synsets[s.get("synset")]) for pos, ss in sorted(options, key=rank) for s in ss]
    if curated_kind:
        same = [x for x in senses if KIND.get(x[0]) == curated_kind]
        senses = same or senses
    if not senses: return None
    if curated_meaning:
        # The curated meaning says which sense the entry is about; pick the dictionary sense closest to it.
        want = words_of(curated_meaning)
        best = max(range(len(senses)), key=lambda i: (len(want & words_of(senses[i][2]["definition"] + " " + " ".join(senses[i][2]["members"]))), -i))
        senses = [senses[best]] + senses[:best] + senses[best + 1:]
    pos, s, syn = senses[0]
    own = next((ex for ex in syn["examples"] if uses(ex, word)), None)
    if own: return pos, s, syn, own
    if curated_sentence: return pos, s, syn, curated_sentence
    for pos, s, syn in senses[1:]:
        for ex in syn["examples"]:
            if uses(ex, word): return pos, s, syn, ex
    return senses[0][0], senses[0][1], senses[0][2], ""


def build(path):
    entries, synsets, sense_by_id = load(path)
    curated = read_curated()
    levels = read_words()
    rows, skipped = [], []
    for level, words in levels.items():
        for word in words:
            cur = curated.get(word)
            got = pick(word, entries, synsets, cur["sentence"] if cur else None, (cur["meaning"] + " " + " ".join(cur["right"])) if cur else None, cur["kind"] if cur else None)
            if got is None:
                skipped.append((word, "not in WordNet")); continue
            pos, sense, syn, example = got
            if not example and cur: example = cur["sentence"]
            if not example:
                skipped.append((word, "no example sentence")); continue
            definition = syn["definition"]
            right = list(syn["members"])
            close, wrong = [], []
            for rel, target in syn["rel"]:
                t = synsets.get(target)
                if not t: continue
                if rel in ("similar", "also"): right += t["members"]
                elif rel in ("hypernym", "instance_hypernym"): close += t["members"]
            # Antonyms hang off senses, and for satellite adjectives off the head they're similar to.
            for r in sense.findall("SenseRelation"):
                if r.get("relType") == "antonym" and r.get("target") in sense_by_id:
                    lemma, sid = sense_by_id[r.get("target")]
                    wrong += [lemma] + synsets[sid]["members"]
            if pos == "s": wrong += antonyms_of_head(syn, synsets, sense_by_id, entries)
            # Definition parts, without usage notes such as "(of animals)" or "(followed by 'to')".
            right += [re.sub(r"\([^)]*\)", " ", p).strip() for p in definition.split(";")]
            # Other senses of the same word: a real meaning, just not the one in the sentence.
            for pos2, ss in entries.get(word.lower(), []):
                for s2 in ss:
                    if s2 is sense: continue
                    d = synsets[s2.get("synset")]["definition"]
                    if d: close.append(d.split(";")[0].strip())
            if cur:
                right += cur["right"]; close += cur["close"]; wrong += cur["wrong"]
            right = clean(right, word)
            wrong = [w for w in clean(wrong, word) if w.lower() not in {r.lower() for r in right}]
            close = [c for c in clean(close, word) if c.lower() not in {r.lower() for r in right} | {w.lower() for w in wrong}][:12]
            if not right:
                skipped.append((word, "nothing to accept besides the word itself")); continue
            kind = KIND.get(pos, "word")
            example = example.strip()
            example = example[0].upper() + example[1:]
            rows.append([word, kind, str(level), example, definition, "; ".join(right), "; ".join(close), "; ".join(wrong)])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Generated by tools/meaning/build_meaning_bank.py from Open English WordNet (CC BY 4.0) and curated_en.txt.\n")
        f.write("# word\tkind\tlevel\tsentence\tdefinition\tright\tclose\twrong\n")
        for r in rows:
            assert all("\t" not in c and "\n" not in c for c in r), r
            f.write("\t".join(r) + "\n")
    print(f"{len(rows)} words written to {OUT}")
    for level in levels: print(f"  level {level}: {sum(1 for r in rows if r[2] == str(level))}")
    for w, why in skipped: print(f"  skipped {w}: {why}")


def antonyms_of_head(syn, synsets, sense_by_id, entries):
    """Satellite adjectives inherit the antonyms of the head adjective they are similar to."""
    out = []
    for rel, target in syn["rel"]:
        if rel != "similar": continue
        head = synsets.get(target)
        if not head: continue
        for lemma in head["members"]:
            for pos, ss in entries.get(lemma.lower(), []):
                for s in ss:
                    if s.get("synset") != target: continue
                    for r in s.findall("SenseRelation"):
                        if r.get("relType") == "antonym" and r.get("target") in sense_by_id:
                            al, asid = sense_by_id[r.get("target")]
                            out += [al] + synsets[asid]["members"]
                            # The opposite head's own similar words are opposites too.
                            for rel2, t2 in synsets[asid]["rel"]:
                                if rel2 == "similar" and t2 in synsets: out += synsets[t2]["members"]
    return out


def read_words():
    levels, cur = {}, None
    for line in open(os.path.join(HERE, "words_en.txt"), encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"): continue
        m = re.fullmatch(r"\[(\d)\]", line)
        if m: cur = int(m.group(1)); levels[cur] = []; continue
        for w in line.split():
            if w not in levels[cur] and all(w not in ws for ws in levels.values()): levels[cur].append(w)
    return levels


def read_curated():
    text = open(os.path.join(HERE, "curated_en.txt"), encoding="utf-8").read()
    text = "\n".join(l for l in text.splitlines() if not l.startswith("#"))
    out = {}
    for block in re.split(r"\n\s*\n", text.strip()):
        lines = [l.strip() for l in block.strip().splitlines()]
        head = lines[0].split("|")
        lst = lambda p: [x.strip() for l in lines if l.startswith(p) for x in l[1:].split(";") if x.strip()]
        out[head[0]] = {"kind": head[1], "level": int(head[2]), "sentence": head[3], "meaning": head[4],
                        "right": lst("+"), "close": lst("~"), "wrong": lst("-")}
    return out


if __name__ == "__main__":
    build(sys.argv[1])
