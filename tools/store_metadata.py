"""Builds fastlane/metadata/android/ (read by F-Droid and IzzyOnDroid) from the Play store files.

usage: python tools/store_metadata.py [versionCode]

Titles and descriptions come from docs/store/listing.md, the changelog for [versionCode] (default: the one
in app/build.gradle.kts) from distribution/whatsnew/, and the icon, feature graphic and phone screenshots
from docs/store/. tools/release.py runs this for each release, so edit those sources, not the output.
"""
import os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(ROOT, 'docs', 'store')
OUT = os.path.join(ROOT, 'fastlane', 'metadata', 'android')
# Play's word games shots lead: they show what's new.
SCREENSHOTS = ['words-1-wordsworn.png', 'phone-1-home.png', 'phone-2-sudoku.png', 'words-2-sprawl.png',
               'words-3-loneletter.png', 'phone-3-common-threads.png', 'phone-4-five-letters.png', 'phone-6-mahjong.png']


def listings():
    text = open(os.path.join(STORE, 'listing.md'), encoding='utf-8').read()
    for block in re.split(r'\n## ', text)[1:]:
        lang = block.split('\n', 1)[0].strip()
        yield lang, {
            'title': re.search(r'Title: (.*)', block).group(1).strip(),
            'short_description': re.search(r'Short description: (.*)', block).group(1).strip(),
            'full_description': block.split('Full description:\n', 1)[1].strip(),
        }


def main():
    gradle = open(os.path.join(ROOT, 'app', 'build.gradle.kts'), encoding='utf-8').read()
    code = sys.argv[1] if len(sys.argv) > 1 else re.search(r'versionCode = (\d+)', gradle).group(1)
    for lang, fields in listings():
        folder = os.path.join(OUT, lang)
        os.makedirs(os.path.join(folder, 'changelogs'), exist_ok=True)
        for name, value in fields.items():
            open(os.path.join(folder, name + '.txt'), 'w', encoding='utf-8', newline='\n').write(value + '\n')
        notes = os.path.join(ROOT, 'distribution', 'whatsnew', 'whatsnew-' + lang)
        if os.path.exists(notes):
            shutil.copyfile(notes, os.path.join(folder, 'changelogs', code + '.txt'))
    # Pictures are shared by every language, so they live under en-US only.
    images = os.path.join(OUT, 'en-US', 'images')
    shutil.rmtree(os.path.join(images, 'phoneScreenshots'), ignore_errors=True)
    os.makedirs(os.path.join(images, 'phoneScreenshots'))
    shutil.copyfile(os.path.join(STORE, 'graphics', 'icon-512.png'), os.path.join(images, 'icon.png'))
    shutil.copyfile(os.path.join(STORE, 'graphics', 'feature-1024x500.png'), os.path.join(images, 'featureGraphic.png'))
    for k, name in enumerate(SCREENSHOTS, 1):
        shutil.copyfile(os.path.join(STORE, 'screenshots', name), os.path.join(images, 'phoneScreenshots', f'{k}.png'))
    print(f'Wrote {os.path.relpath(OUT, ROOT)} (changelog {code}).')


if __name__ == '__main__':
    main()
