"""Exercise auth, ZIP submission, output delivery and unconfigured AI."""
import io,json,os,pathlib,sys,time,urllib.error,urllib.request,uuid,zipfile
BASE=sys.argv[1]
ZIP=pathlib.Path(sys.argv[2]).read_bytes()
TOKEN=os.environ['BUILDER_AUTH_TOKEN']
def request(path,data=None, extra=None, secret=TOKEN, expected=200):
    headers={'Authorization':'Bearer '+secret}
    if extra:headers.update(extra)
    req=urllib.request.Request(BASE+path,data=data,headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=20) as r:
            assert r.status==expected,(r.status,expected)
            return r.read()
    except urllib.error.HTTPError as e:
        assert e.code==expected,(e.code,expected)
        return e.read()
for _ in range(35):
    try:
        request('/v1/jobs/'+'f'*32,secret='wrong',expected=401);break
    except OSError:time.sleep(.4)
else:raise RuntimeError('Service unavailable')
request('/v1/generate',b'{"prompt":"test calculator"}',expected=503)
request('/v1/jobs/'+'b'*32+'/apk',ZIP,{'X-Builder-Permissions':'UNKNOWN'},expected=400)
for fmt, filename in [('apk','AndroidManifest.xml'),('aab','BundleConfig.pb')]:
    path='/v1/jobs/'+uuid.uuid4().hex
    request(path+'/'+fmt,ZIP,{'X-Builder-Permissions':'INTERNET,RECORD_AUDIO'},expected=202)
    for _ in range(125):
        state=json.loads(request(path))['status']
        if state in ('complete','failed'):break
        time.sleep(1)
    assert state=='complete',state
    payload=request(path+'/file')
    with zipfile.ZipFile(io.BytesIO(payload)) as z:
        assert filename in z.namelist() and z.testzip() is None
    pathlib.Path('/tmp/builder-service-check.'+fmt).write_bytes(payload)
    print('HTTP build and download passed:',fmt)
print('Invalid credentials, invalid permission and unconfigured AI checks passed.')
