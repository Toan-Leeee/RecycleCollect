from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import shutil

root = Path(__file__).resolve().parents[1]
out = root.parent / 'outputs' / 'RecycleCollect'
out.mkdir(parents=True, exist_ok=True)
apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
shutil.copy2(apk, out / 'ThuGomTaiChe-UI.apk')
with ZipFile(out / 'RecycleCollect-Source.zip', 'w', ZIP_DEFLATED) as archive:
    for file in root.rglob('*'):
        rel = file.relative_to(root)
        if not file.is_file() or any(part in {'.gradle', '.idea', 'build', '__pycache__'} for part in rel.parts):
            continue
        if file.name == 'local.properties' or file.suffix == '.log':
            continue
        archive.write(file, Path('RecycleCollect') / rel)
print(out)
