"""Regression checks for context selection; no WordNet download required."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

import build_meaning_bank as bank


class ContextTests(unittest.TestCase):
    def test_shipped_bank_matches_reviewed_context_and_sense(self):
        contexts = bank.read_contexts()
        rows = [line.split('\t') for line in Path(bank.OUT).read_text(encoding='utf-8').splitlines()
                if line and not line.startswith('#')]
        self.assertEqual(set(contexts), {row[0] for row in rows})
        for word, kind, level, sentence, meaning, *_ in rows:
            self.assertEqual((kind, meaning, sentence), contexts[word], word)
            bank.validate_context(word, sentence)

    def test_fragments_and_missing_target_are_rejected(self):
        for sentence in ('Ancient history', 'A generous donation.', '',
                         'The kind neighbor shared food with every family and asked for nothing in return.'):
            with self.assertRaises(ValueError):
                bank.validate_context('generous', sentence)

    def test_context_selects_its_exact_sense_not_first_example(self):
        senses = [ET.Element('Sense', synset='edge'), ET.Element('Sense', synset='image')]
        entries = {'sharp': [('a', senses)]}
        synsets = {'edge': {'definition': 'having a cutting edge', 'examples': ['A sharp knife']},
                   'image': {'definition': 'clearly defined', 'examples': ['A sharp image']}}
        sentence = 'The sharp photograph showed every thread in the fabric without a single blurry edge.'
        result = bank.pick_context('sharp', entries, synsets, ('adjective', 'clearly defined', sentence))
        self.assertIs(result[1], senses[1])
        self.assertEqual(result[3], sentence)
        with self.assertRaises(ValueError):
            bank.pick_context('sharp', entries, synsets, ('adjective', 'changed definition', sentence))
        with self.assertRaises(ValueError):
            bank.pick_context('sharp', entries, synsets, ('noun', 'clearly defined', sentence))


if __name__ == '__main__':
    unittest.main()
