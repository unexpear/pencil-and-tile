"""Labels every WordNet word family with the local Laya classifier, for the word games' lists.

usage (Laya's own Python):
  D:\\ai\\Local-Models\\runtime\\Scripts\\python.exe tools/words/laya_classify.py enable1.txt english-wordnet-2025.xml.gz

For each base word (an Open English WordNet lemma that ENABLE accepts) Laya reads the word and its most
common definition and answers three questions: how familiar it is, whether it suits a family game, and which
everyday category it belongs to (for Lone Letter). Results go to tools/words/laya_labels.tsv; the run can be
stopped and started again, it picks up where it left off.
"""
import gzip, os, re, sys, time, xml.etree.ElementTree as ET

import numpy as np
import torch

LAYA = r'D:\ai\Local-Models\models\Laya'
sys.path.insert(0, r'D:\ai\Local-Models\sources\Laya')
import laya  # noqa: E402
from laya.common import QTYPES, build_sequence, collate_items  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'laya_labels.tsv')
BATCH = 24

QUESTIONS = {
    'familiar': {'type': 'choice', 'instructions': 'How well known is this English word to an ordinary person?',
                 'criteria': {'everyday': 'used in daily life, known to children',
                              'known': 'most adults know it',
                              'rare': 'unusual, literary, old-fashioned or regional',
                              'technical': 'science, medicine, law or other specialist jargon'}},
    'family': {'type': 'choice', 'instructions': 'Is this word suitable for a family word game played by children?',
               'criteria': {'safe': 'fine for all ages',
                            'mild': 'a little rude, scary or grown-up',
                            'adult': 'sexual, a slur, swearing or drugs'}},
    'category': {'type': 'choice', 'instructions': 'Which category does this word name an example of?',
                 'criteria': {'animal': 'an animal, bird, fish or insect', 'food': 'a food or drink', 'plant': 'a plant, tree or flower',
                              'job': 'a job or role a person has', 'sport': 'a sport or game', 'clothing': 'clothing or jewelry',
                              'body': 'a part of the body', 'home': 'a thing found in a home', 'tool': 'a tool or machine',
                              'vehicle': 'a vehicle or transport', 'place': 'a kind of place or building', 'nature': 'weather, landform or sky',
                              'music': 'a musical instrument or music', 'color': 'a color', 'feeling': 'a feeling or mood',
                              'none': 'none of these'}},
}


def lemmas_with_definitions(enable, wordnet_path):
    root = ET.parse(gzip.open(wordnet_path)).getroot()
    lex = root.find('Lexicon')
    definition = {s.get('id'): (s.findtext('Definition') or '').strip() for s in lex.findall('Synset')}
    out = {}
    for e in lex.findall('LexicalEntry'):
        lemma = e.find('Lemma')
        w = lemma.get('writtenForm')
        if not (w.isalpha() and w.islower() and w in enable) or w in out:
            continue
        sense = e.find('Sense')
        if sense is not None:
            out[w] = (lemma.get('partOfSpeech'), definition.get(sense.get('synset'), ''))
    return out


def main():
    enable = {w.strip().lower() for w in open(sys.argv[1], encoding='ascii') if w.strip()}
    words = lemmas_with_definitions(enable, sys.argv[2])
    done = set()
    if os.path.exists(OUT):
        done = {line.split('\t')[0] for line in open(OUT, encoding='utf-8') if line.strip()}
    todo = sorted(w for w in words if w not in done)
    if os.environ.get('LIMIT'):
        todo = todo[:int(os.environ['LIMIT'])]
    print(f'{len(words)} base words, {len(done)} done, {len(todo)} to go', flush=True)
    agent = laya.load(LAYA, device='cuda')
    qs = {k: agent._to_internal(v) for k, v in QUESTIONS.items()}
    keys = {k: list(q['crit'].keys()) for k, q in qs.items()}
    out = open(OUT, 'a', encoding='utf-8', newline='\n')
    if not done:
        out.write('# word\tpos\tfamiliar\tp\tfamily\tp\tcategory\tp\n')
    t0 = time.time()
    for start in range(0, len(todo), BATCH):
        chunk = todo[start:start + BATCH]
        groups, order = [], []
        for w in chunk:
            pos, gloss = words[w]
            state = {'word': w, 'meaning': gloss[:300]}
            group = []
            for qid, q in qs.items():
                seq, markers = build_sequence(agent.tok, state, q, agent.cfg.get('max_len', 512), agent.cfg.get('head_max_len', 192))
                group.append({'ids': seq, 'markers': markers, 'qtype': QTYPES[q['t']]})
                order.append((w, qid, len(markers)))
            groups.append(group)
        b = collate_items(groups, agent.tok.pad_token_id)
        with torch.no_grad(), torch.autocast(device_type='cuda', dtype=agent.dtype):
            logits, _ = agent.model(b['input_ids'].to(agent.device), b['attention_mask'].to(agent.device),
                                    b['marker_pos'].to(agent.device), b['marker_mask'].to(agent.device), b['qtype'].to(agent.device))
        logits = logits.float().cpu().numpy()
        answers = {}
        for r, (w, qid, k) in enumerate(order):
            z = logits[r, :k]
            p = np.exp(z - z.max()); p /= p.sum()
            i = int(p.argmax())
            answers.setdefault(w, {})[qid] = (keys[qid][i], float(p[i]))
        for w in chunk:
            a = answers[w]
            out.write('\t'.join([w, words[w][0], a['familiar'][0], f"{a['familiar'][1]:.3f}", a['family'][0], f"{a['family'][1]:.3f}",
                                 a['category'][0], f"{a['category'][1]:.3f}"]) + '\n')
        out.flush()
        n = start + len(chunk)
        if n % (BATCH * 40) < BATCH or n == len(todo):
            rate = n / max(1e-6, time.time() - t0)
            print(f'{n}/{len(todo)} words, {rate:.1f}/s, about {(len(todo) - n) / max(rate, 1e-6) / 60:.0f} min left', flush=True)
    out.close()


if __name__ == '__main__':
    main()
