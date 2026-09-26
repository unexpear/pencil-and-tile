"""Builds the word lists shared by the letter games.

usage: python tools/words/build_dictionary.py path/to/enable1.txt path/to/english-wordnet-2025.xml.gz

Writes to sudoku-engine/src/main/resources/words/:
  all.txt.gz     every accepted word, 2 to 15 letters, from ENABLE (public domain)
  common.txt.gz  the everyday tier: ENABLE words whose word family (an Open English WordNet lemma, CC BY 4.0,
                 and its regular inflections) turns up often in WordNet's own definitions and examples, which
                 are plain everyday English; games use it for targets, answers and hints

Words in blocked.txt (rude words and slurs) are left out of both, so no game accepts or shows them.
"""
import gzip, os, re, sys, xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'sudoku-engine', 'src', 'main', 'resources', 'words')
# A word family must be used this often in WordNet's glosses to count as everyday.
FAMILY_USES = 12


def blocked():
    words = set()
    for line in open(os.path.join(HERE, 'blocked.txt'), encoding='utf-8'):
        line = line.strip().lower()
        if line and not line.startswith('#'):
            words.add(line)
    return words


def is_blocked(word, bad):
    # Whole words and their simple inflections.
    if word in bad:
        return True
    for suffix in ('s', 'es', 'ed', 'ing', 'er', 'ers', 'y', 'ies'):
        if word.endswith(suffix) and word[:-len(suffix)] in bad:
            return True
    return False


def inflections(lemma):
    """Regular English inflections, checked against ENABLE afterwards."""
    out = {lemma, lemma + 's', lemma + 'es', lemma + 'ed', lemma + 'd', lemma + 'ing', lemma + 'er', lemma + 'est'}
    if lemma.endswith('y'):
        out |= {lemma[:-1] + 'ies', lemma[:-1] + 'ied', lemma[:-1] + 'ier', lemma[:-1] + 'iest'}
    if lemma.endswith('e'):
        out |= {lemma[:-1] + 'ing', lemma + 'd', lemma + 'r', lemma + 'st'}
    if re.fullmatch(r'.*[^aeiou][aeiou][bdgklmnprt]', lemma):
        c = lemma[-1]
        out |= {lemma + c + 'ed', lemma + c + 'ing', lemma + c + 'er', lemma + c + 'est'}
    return out


def main():
    enable_path, wordnet_path = sys.argv[1], sys.argv[2]
    bad = blocked()
    enable = {w.strip().lower() for w in open(enable_path, encoding='ascii') if w.strip()}
    enable = {w for w in enable if w.isalpha() and 2 <= len(w) <= 15 and not is_blocked(w, bad)}
    root = ET.parse(gzip.open(wordnet_path)).getroot()
    lemmas = set()
    for e in root.iter('LexicalEntry'):
        w = e.find('Lemma').get('writtenForm')
        if w.isalpha() and w.islower():
            lemmas.add(w)
    # How often each word appears in the definitions and examples: a frequency list from plain English.
    uses = {}
    for synset in root.iter('Synset'):
        for tag in ('Definition', 'Example'):
            for e in synset.findall(tag):
                for w in re.findall(r'[a-z]+', (e.text or '').lower()):
                    uses[w] = uses.get(w, 0) + 1
    common = set()
    for lemma in lemmas:
        family = inflections(lemma) & enable
        if sum(uses.get(w, 0) for w in family) >= FAMILY_USES:
            common |= family
    os.makedirs(OUT, exist_ok=True)
    for name, words in (('all.txt.gz', enable), ('common.txt.gz', common)):
        with gzip.open(os.path.join(OUT, name), 'wt', encoding='ascii', newline='\n') as f:
            f.write('\n'.join(sorted(w.upper() for w in words)) + '\n')
        print(f'{name}: {len(words)} words')


if __name__ == '__main__':
    main()
