from pathlib import Path
import re
base=Path('app/src/main/java/tj/builder/v1')
translations=(base/'LocalizedStatus.java').read_text()
rows=re.findall(r'add\("([^"]+)","([^"]+)","([^"]+)","([^"]+)"\)',translations)
loc={x[0]:x[1:] for x in rows}
assert len(rows)==len(loc)
tajik=re.compile(r'[ҚқҶҷӢӣҒғҲҳӮӯ]')
for tg,(ru,en,fa) in loc.items():
    assert not tajik.search(ru),ru
    assert not re.search(r'[А-Яа-яЁё]',en),en
    assert len(fa.strip())>0
for file in ['MainActivity.java','BuildConnection.java','ProjectReview.java']:
    for line in (base/file).read_text().splitlines():
        if re.search(r'(?<!\w)t\("|BuilderLocale\.t\(',line): continue
        for item in re.findall(r'"((?:[^"\\]|\\.)*)"',line):
            if re.search(r'[А-Яа-яЁёҚқҶҷӢӣҒғҲҳӮӯ]',item):
                assert item in loc,(file,item)
wizard=(base/'ProjectWizard.java').read_text()
assert wizard.index('    render();')<wizard.index('    dialog.show();')
for marker in ['FLAG_ALT_FOCUSABLE_IM','SOFT_INPUT_ADJUST_RESIZE','setShowSoftInputOnFocus(true)','R.id.project_name','R.id.project_package']:
    assert marker in wizard,marker
home=(base/'MainActivity.java').read_text()
for removed in ['button(box,"Интихоби HTML ё ZIP"','button(box,"Санҷиши ZIP-и лоиҳаи Android"','TextView aiHeading']:
    assert removed not in home,removed
print('Translation coverage, project-only file selection, and input window configuration verified.')
