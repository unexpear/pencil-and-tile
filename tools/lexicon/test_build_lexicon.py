"""Checks lexicon matching rules without downloading WordNet."""
import importlib.util
import os
import unittest

import build_lexicon_db as lex

HERE = os.path.dirname(os.path.abspath(__file__))


def dictionary_builder():
    path = os.path.join(HERE, os.pardir, "words", "build_dictionary.py")
    spec = importlib.util.spec_from_file_location("build_dictionary", os.path.abspath(path))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class LexiconRulesTest(unittest.TestCase):
    def test_block_rule_matches_the_letter_game_dictionary(self):
        other = dictionary_builder()
        bad = {"butt", "ass", "spic", "shit"}
        for word in ("butt", "butter", "ass", "assess", "asses", "cocktail", "dog", "spicy", "shit", "shits"):
            self.assertEqual(lex.is_blocked(word, bad), other.is_blocked(word, bad), word)

    def test_suffix_false_positives_keep_a_definition(self):
        bad = lex.load_blocked(os.path.join(HERE, os.pardir, "words", "blocked.txt"))
        self.assertFalse(lex.excluded("butter", bad))
        self.assertFalse(lex.excluded("assess", bad))
        self.assertFalse(lex.excluded("spicy", bad))
        self.assertTrue(lex.excluded("shit", bad))
        self.assertTrue(lex.excluded("fucking", bad))
        self.assertTrue(lex.excluded("ice shit", bad))

    def test_lemma_rules(self):
        bad = {"shit"}
        playable = {"paris", "dog"}
        self.assertEqual(lex.normalize_lemma("Ice_Cream"), "ice cream")
        self.assertTrue(lex.keep_oewn_lemma("dog", "dog", playable, bad))
        self.assertTrue(lex.keep_oewn_lemma("Paris", "paris", playable, bad))
        self.assertFalse(lex.keep_oewn_lemma("Paris", "paris", set(), bad))
        self.assertFalse(lex.keep_oewn_lemma("New York", "new york", playable, bad))
        self.assertTrue(lex.keep_oewn_lemma("ice cream", "ice cream", playable, bad))
        self.assertFalse(lex.keep_oewn_lemma(".22", ".22", playable, bad))
        self.assertFalse(lex.keep_oewn_lemma("shit", "shit", playable, bad))

    def test_word_boundary_match(self):
        self.assertTrue(lex.contains_lemma("The dog barked all night.", "dog"))
        self.assertTrue(lex.contains_lemma("The dog's bone is buried.", "dog"))
        self.assertFalse(lex.contains_lemma("The dogma was strict.", "dog"))
        self.assertFalse(lex.contains_lemma("He is running home now.", "run"))
        self.assertFalse(lex.contains_lemma("Hot dogs are sandwiches.", "hot dog"))
        self.assertTrue(lex.contains_lemma("I like hot dog buns.", "hot dog"))
        self.assertFalse(lex.contains_lemma("Tom grew up in the hood.", "'hood"))
        self.assertTrue(lex.contains_lemma("Tom grew up in the 'hood.", "'hood"))

    def test_tatoeba_sentence_is_short_and_exact(self):
        bad = {"shit"}
        rows = [
            (1, "The dogma was old and dull."),
            (2, "The dog barked all night long."),
            (3, "Shit happens to everyone today."),
            (4, "DOG DOG DOG DOG DOG DOG."),
            (5, "A tiny dog sat."),
            (6, "A tiny dog sat down."),
            (9, "See http://example.com/dog for the rest of it."),
        ]
        index, kept = lex.index_sentences(rows, bad)
        self.assertEqual(kept, 3)
        self.assertEqual(lex.pick_tatoeba("dog", index), "A tiny dog sat down.")
        self.assertIsNone(lex.pick_tatoeba("the", index))
        self.assertIsNone(lex.pick_tatoeba("cat", index))

    def test_oewn_example_prefers_the_lemma(self):
        examples = ["The policeman chased the mugger down the alley.", "They dog his every step."]
        self.assertEqual(lex.pick_oewn_example("dog", examples), "They dog his every step.")
        self.assertEqual(lex.pick_oewn_example("chase", examples[:1]), examples[0])


if __name__ == "__main__":
    unittest.main()
