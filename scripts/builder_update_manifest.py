"""Write update metadata for the official Builder, never for imported user projects."""
import argparse, hashlib, json, pathlib, re, subprocess
from urllib.parse import urlsplit
def run(*args):
    return subprocess.run(args,check=True,stdout=subprocess.PIPE,text=True).stdout
def create(apk, url, expected_cert, notes, previous, aapt, signer):
    url_info=urlsplit(url)
    if url_info.scheme!='https' or not url_info.hostname or url_info.username or url_info.fragment:
        raise ValueError('APK URL must be HTTPS without credentials or fragment')
    if not re.fullmatch('[0-9a-fA-F]{64}',expected_cert):raise ValueError('Pin the release certificate first')
    certs=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})$',run(signer,'verify','--print-certs',str(apk)),re.M)
    if certs!=[expected_cert.lower()]:raise ValueError('APK is not signed by the pinned Builder release key')
    badging=run(aapt,'dump','badging',str(apk))
    m=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging,re.M)
    if not m or m[1]!='tj.builder.app':raise ValueError('Not the official Builder package')
    code=int(m[2])
    if code<=previous:raise ValueError('Version code must increase')
    if not all(isinstance(notes.get(lang),str) and 0<len(notes[lang].strip())<=5000 for lang in ('tg','ru','en','fa')):
        raise ValueError('Supply release notes in all four languages')
    return {'schema':1,'packages':{'tj.builder.app':{'channel':'direct','version':m[3],'code':code,'notes':notes,
        'url':url,'size':apk.stat().st_size,'sha256':hashlib.file_digest(apk.open('rb'),'sha256').hexdigest()}}}
def main():
    p=argparse.ArgumentParser()
    p.add_argument('apk',type=pathlib.Path);p.add_argument('--url',required=True);p.add_argument('--certificate',required=True)
    p.add_argument('--notes',type=pathlib.Path,required=True);p.add_argument('--previous',type=int,required=True)
    p.add_argument('--aapt',default='aapt');p.add_argument('--apksigner',default='apksigner')
    a=p.parse_args()
    print(json.dumps(create(a.apk,a.url,a.certificate,json.loads(a.notes.read_text()),a.previous,a.aapt,a.apksigner),ensure_ascii=False,indent=2))
if __name__=='__main__':main()
