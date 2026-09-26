"""Cuts a release: bumps the version, commits, tags and pushes, and GitHub Actions uploads it to Google Play.

usage: python tools/release.py 1.1.0            # set versionName, bump versionCode by one
       python tools/release.py 1.1.0 --dry-run  # show what would happen, change nothing

The tag (v1.1.0) starts .github/workflows/release.yml, which runs the tests, builds the signed bundle and
sends it to the closed-testing track with the notes in distribution/whatsnew/.
"""
import os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(ROOT, 'app', 'build.gradle.kts')
NOTES = os.path.join(ROOT, 'distribution', 'whatsnew')
LOCALES = ['en-US', 'de-DE', 'es-ES', 'ja-JP', 'zh-CN']


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()


def fail(message):
    print('Not released: ' + message)
    sys.exit(1)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    dry = '--dry-run' in sys.argv
    if len(args) != 1 or not re.fullmatch(r'\d+\.\d+\.\d+', args[0]):
        fail('give the new version, like: python tools/release.py 1.1.0')
    name = args[0]
    tag = 'v' + name

    gradle = open(GRADLE, encoding='utf-8').read()
    code = int(re.search(r'versionCode = (\d+)', gradle).group(1)) + 1
    old = re.search(r'versionName = "([^"]+)"', gradle).group(1)

    # Release notes: every language present and within Play's 500-character limit.
    for locale in LOCALES:
        path = os.path.join(NOTES, 'whatsnew-' + locale)
        if not os.path.exists(path):
            fail(f'missing release notes {os.path.relpath(path, ROOT)}')
        text = open(path, encoding='utf-8').read().strip()
        if not text or len(text) > 500:
            fail(f'{locale} release notes must be 1 to 500 characters (they are {len(text)})')

    if git('status', '--porcelain'):
        fail('the working tree has uncommitted changes. Commit them first.')
    if git('branch', '--show-current') != 'main':
        fail('releases are cut from main.')
    if git('tag', '--list', tag):
        fail(f'tag {tag} already exists.')

    print(f'Release {tag}: versionName {old} -> {name}, versionCode -> {code}')
    if dry:
        print('--dry-run: nothing changed.')
        return
    git('fetch', 'origin', 'main', '--tags')
    if git('tag', '--list', tag):
        fail(f'tag {tag} already exists on GitHub.')
    try:
        git('merge-base', '--is-ancestor', 'origin/main', 'HEAD')
    except subprocess.CalledProcessError:
        fail('main is behind GitHub. Pull first.')

    gradle = re.sub(r'versionCode = \d+', f'versionCode = {code}', gradle, count=1)
    gradle = re.sub(r'versionName = "[^"]+"', f'versionName = "{name}"', gradle, count=1)
    with open(GRADLE, 'w', encoding='utf-8', newline='') as f:
        f.write(gradle)
    git('add', GRADLE)
    # F-Droid's copy of the store text and this release's changelog.
    subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'store_metadata.py'), str(code)], cwd=ROOT, check=True)
    git('add', 'fastlane')
    git('commit', '-m', f'Release {tag} (version code {code})')
    git('tag', '-a', tag, '-m', f'Pencil & Tile {name}')
    git('push', '--atomic', 'origin', 'main', tag)
    print(f'Pushed {tag}. Follow the upload with: gh run watch --repo unexpear/pencil-and-tile')


if __name__ == '__main__':
    main()
