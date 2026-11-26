"""Compare the installed-source selector directly with the user's Sauce checkout.
No whitespace normalization or generated replacement of the component is allowed.
"""
from pathlib import Path
import argparse,hashlib,json
parser=argparse.ArgumentParser()
parser.add_argument("sauce_root", type=Path, help="Path to the Sauce Tracker Rewrite source checkout")
SAUCE=parser.parse_args().sauce_root
ROOT=Path(__file__).resolve().parents[1]
META=json.loads((ROOT/'docs/theme-source-provenance.json').read_text(encoding='utf-8'))
def digest(text):return hashlib.sha256(text.encode('utf-8')).hexdigest()
def between(text,start,end):return text.split(start,1)[1].split(end,1)[0].strip()+'\n'
source_path=(SAUCE/META['sourceFile']);source=source_path.read_text(encoding='utf-8')
a=source.index('@Composable\ninternal fun ThemeToggleWithAccentPicker(')
b=source.index('\ninternal fun renderStars',a)
upstream=source[a:b].strip()+'\n'
local_path=ROOT/'app/src/main/java/com/roinur/caffeinetracker/core/ui/components/DashboardThemeControl.kt'
local=between(local_path.read_text(encoding='utf-8'),'// BEGIN SAUCE THEME SOURCE','// END SAUCE THEME SOURCE')
assert local==upstream,'Selector/helper code differs from the actual Sauce source'
assert digest(local)==META['componentSourceSha256'],'Selector differs from the pinned original'
assert hashlib.sha256(source_path.read_bytes()).hexdigest()==META['sourceFileSha256'],'Upstream source file changed after extraction'
cycle_source=(SAUCE/META['cycleSourceFile']).read_text(encoding='utf-8')
a=cycle_source.index('when (themeMode)',cycle_source.index('fun cycleThemeMode()'))
b=cycle_source.index('\n        }',a)+len('\n        }')
cycle=cycle_source[a:b]
local_cycle=between((ROOT/'app/src/main/java/com/roinur/caffeinetracker/core/ui/theme/ThemeModeCycle.kt').read_text(encoding='utf-8'),'// BEGIN SAUCE THEME CYCLE','// END SAUCE THEME CYCLE').rstrip('\n')
assert cycle==local_cycle,'Theme cycle differs from the Sauce source expression'
assert digest(cycle)==META['cycleSourceSha256'],'Theme cycle differs from pinned original'
asset=(ROOT/'app/src/main/res/drawable/ic_incognito_24.xml').read_bytes()
assert asset==(SAUCE/META['assetSourceFile']).read_bytes(),'Supporting source asset differs'
assert hashlib.sha256(asset).hexdigest()==META['assetSha256']
print('PASS: original selector, symbols, accent options and supporting asset match upstream exactly.')
print('PASS: original System -> Dark -> Light -> System expression matches upstream exactly.')
print('Component SHA-256: '+digest(local))
