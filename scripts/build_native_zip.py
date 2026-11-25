"""Build a trusted Android project ZIP in an isolated, disposable Gradle environment."""
import argparse
import pathlib
import shutil
import stat
import subprocess
import tempfile
import zipfile

LIMIT=100*1024*1024
MAX_ENTRIES=4000
SETTINGS={'settings.gradle','settings.gradle.kts'}

def extract(zip_path, to):
    if zip_path.stat().st_size>LIMIT:
        raise ValueError('ZIP is over 100 MB')
    with zipfile.ZipFile(zip_path) as archive:
        infos=archive.infolist()
        if len(infos)>MAX_ENTRIES or sum(i.file_size for i in infos)>LIMIT:
            raise ValueError('ZIP expanded size or file count exceeds the limit')
        seen=set()
        for i in infos:
            name=i.filename.rstrip('/')
            parts=name.split('/')
            mode=(i.external_attr >> 16) & 0o170000
            if (not name or '\\' in name or ':' in name or '\0' in i.orig_filename
                or any(p in ('','.','..') for p in parts) or len(name)>300
                or mode not in (0,stat.S_IFREG,stat.S_IFDIR) or name in seen
                or any(p in ('.git','.gradle') for p in parts)):
                raise ValueError('Invalid or ambiguous ZIP entry')
            seen.add(name)
            p=to.joinpath(*parts)
            if i.is_dir():
                p.mkdir(parents=True,exist_ok=True)
                continue
            p.parent.mkdir(parents=True,exist_ok=True)
            with archive.open(i) as src,p.open('xb') as dst:
                shutil.copyfileobj(src,dst)
    def project(p):
        return any((p/s).is_file() for s in SETTINGS) and (p/'app/src/main/AndroidManifest.xml').is_file() and any((p/'app'/s).is_file() for s in ('build.gradle','build.gradle.kts'))
    if project(to):return to
    matches=[p for p in to.iterdir() if p.is_dir() and project(p)]
    if len(matches)==1:return matches[0]
    raise ValueError('Expected one Gradle Android project with an app module')

def build(src, dest, fmt, gradle, trusted):
    if not trusted:
        raise ValueError('Gradle runs project code. Use a disposable isolated runner and explicitly mark the source trusted.')
    with tempfile.TemporaryDirectory(prefix='android-zip-') as d:
        project=extract(src,pathlib.Path(d))
        requested={'apk':('assembleDebug',),'aab':('bundleDebug',),'both':('assembleDebug','bundleDebug')}[fmt]
        subprocess.run([gradle,'--offline','--no-daemon','--no-build-cache',*(':app:'+t for t in requested)],cwd=project,check=True,timeout=300)
        dest.mkdir(parents=True,exist_ok=True)
        for ext, inner, leaf in [('apk','apk','app-debug.apk'),('aab','bundle','app-debug.aab')]:
            if fmt not in (ext,'both'):continue
            f=(project/'app/build/outputs'/inner/'debug'/leaf)
            if f.is_symlink() or not f.is_file() or not f.resolve().is_relative_to(project.resolve()):
                raise ValueError('Expected standard debug '+ext.upper()+' was not generated')
            with zipfile.ZipFile(f) as z:
                if not z.infolist() or z.testzip() is not None:raise ValueError('Output archive failed its check')
            shutil.copyfile(f,dest/leaf)

def main():
    p=argparse.ArgumentParser()
    p.add_argument('source',type=pathlib.Path);p.add_argument('destination',type=pathlib.Path)
    p.add_argument('--format',choices=('apk','aab','both'),default='both')
    p.add_argument('--gradle',default='gradle')
    p.add_argument('--trusted-project',action='store_true')
    a=p.parse_args();build(a.source.resolve(),a.destination.resolve(),a.format,a.gradle,a.trusted_project)
if __name__=='__main__':main()
