# Wordsworn rules and structure

Updated September 26, 2026. The owner requested the reference game's gameplay
framework with Wordsworn's own characters, environments and creative content.
This supersedes the proposal to invent unrelated hero systems.

## Reference

The final tabletop rulebook has six encounters: one regular enemy and one boss
in each of three books. The Tabletopia prototype and older digital description
use a different nine-fight structure and are not the implementation reference.

- [Final publisher-authored rulebook, hosted by Gamer's HQ](https://gamers-hq.de/media/pdf/9a/4d/4f/Paperback_Adventures_rulebook.pdf)
- [Publisher FAQ and errata](https://www.fowers.games/pages/paperback-adventures)

The scope is the solo gameplay framework. Cooperative variants are not included.

## Rule-set 3

- All difficulties visit three books and six fights. Each guardian is seeded
  from its book's three choices; the existing boss closes that book. Both enemy
  stages, the upcoming boss, the deck and shop stock can be inspected.
- Four-card hands, left/right icons, top-card abilities, fatigue, discard
  reshuffling and visible actions remain. Enemy actions resolve before the
  current word enters the discard/fatigue piles.
- Wilds and blots pass the top-card role to the next eligible letter. The enemy
  vowel advances the action when used on top, then fatigues. Temporary chosen
  letters do not consume the unused-permanent-wild ink bonus.
- Hex counters have no automatic damage or decay. Explicit core abilities and
  enemy rules give them effects. Ink and hexes clear between fights; health and
  stars persist. There is no automatic healing or star payout for a victory.
- Each hero starts with two core abilities and one ordinary item. One core uses
  stars; the other uses enemy hexes. Direct HP loss bypasses block and can flip
  or defeat an enemy during preparation.
- A complete victory unlocks two alternate cores for that hero. Choosing each
  resource's core independently gives four loadouts per hero. Unlocks are
  recorded separately from the current-run save.
- Each run has a finite 50-card generated library and seven-card blot supply.
  Played blots return to their supply; exhaustion does not invent extras.
- Guardian rewards replace letters; boss rewards add letters and offer boss
  keepsakes. Rewards belong to the enemy. Selected reward steps must be resolved.
  Unchosen offered letters return to the library; replaced cards leave the run.
  Health loss in a reward can end a run.
- The persistent shop reserves three items, two paired keepsakes and three
  letters, refills purchases from finite stocks, sells letter upgrades and
  permits one free row/column refresh per visit. Paid choices cannot be skipped;
  empty slots cannot be bought. Inspection during battle does not permit buying.
- Four optional, independently authored rule modifiers can be combined before
  starting: extra health, another wild, extra enemy block, and starting blots.
- Hints consider alternative duplicate-letter, vowel and wild assignments in
  both directions, bounded at 50,000 assignments. They remain immediate
  heuristic suggestions, not proof of optimal multi-turn play or a winning run.

## Original content and differences

Characters, environments, art, wording, enemy values, core abilities, card
content, reward paths and modifier effects are ours. This is not a card-for-card
implementation or a claim to support every reference-game card exception.

The four existing difficulty presets retain their enemy-stat multipliers.
Easy has 25 health and two permanent wilds; other presets start at 20 health.
These are Wordsworn presets, not exact reproductions of Training Mode. Original
enemy hex rules are displayed, including immunity to direct hex damage.

Content comprises three heroes, four unlocked loadouts each, 12 two-stage
monsters, 11 shared item types, 16 keepsake effects and 16 modifier subsets.
With four difficulties, that is 768 selectable configurations after all unlocks
(3 x 4 x 4 x 16), not 768 unique deals. Finite seeded content may eventually repeat.

## Saves and verification

New runs use version 3. Version-2 saves keep their original campaign, status
rules, rewards and shop and show an earlier-rules label. Version 1 is still
unsupported. Malformed tokens, unsupported versions and invalid phase actions
are rejected. Tests cover resource use, phases, reward/shop exhaustion, alternate
cores, modifiers, replay, hints and persistent unlocks. See VERIFICATION.md for
completed build/device checks. These tests do not establish universal balance.

## Publication

Reference PDFs and screenshots stay in the ignored build directory and are not
shipped. Do not imply affiliation or use the source game's brand in marketing.
Maintain asset/data provenance and licenses. US guidance distinguishes gameplay
methods from protected text/art; trademarks are a separate consideration. This
is not legal clearance. The working name and finished release still need proper
clearance and an IP professional's review; no guarantee against claims is made.

- [US Copyright Office: Games](https://www.copyright.gov/register/tx-games.html)
- [USPTO: clearance searches](https://www.uspto.gov/trademarks/search/comprehensive-clearance-search-similar-trademarks)
- [Google Play: intellectual property](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en)
