"""从 Noto Sans CJK Regular 字体集更新官网展示字体；需要 fonttools。"""
import sys
from pathlib import Path
from fontTools import subset
from fontTools.ttLib import TTFont

shared = Path(__file__).resolve().parents[2] / 'app' / 'shared'
font = TTFont(sys.argv[1], fontNumber=2)
text = ''.join(p.read_text() for p in (shared / 'src/commonMain/composeResources').glob('values*/strings.xml'))
text += ''.join(chr(i) for i in range(32, 127)) + 'ReaderBrowserDocumentsDemoGazeKeep—…'
options = subset.Options()
options.name_IDs = ['*']
options.name_legacy = True
options.name_languages = ['*']
subsetter = subset.Subsetter(options)
subsetter.populate(text=text)
subsetter.subset(font)
for record in font['name'].names:
    if record.nameID in [1, 4, 6, 16]:
        record.string = ('GazeKeepShowcaseSans' if record.nameID == 6 else 'GazeKeep Showcase Sans').encode(record.getEncoding())
font.save(shared / 'src/showcaseMain/composeResources/files/showcase_cjk.otf')
