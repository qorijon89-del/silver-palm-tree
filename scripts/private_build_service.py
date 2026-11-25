"""Single-owner Android build gateway. Bind loopback behind a private TLS proxy.

The configured worker MUST provide filesystem/process/network isolation. Use the
supplied container runner, or the synthetic CI runner for testing only.
"""
import argparse, hmac, json, os, pathlib, re, subprocess, tempfile, threading, time
from build_native_zip import PERMISSIONS
from generate_html import generate_html
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX=100*1024*1024
VALID_ID=re.compile(r'[a-f0-9]{32}\Z')
FORMATS={'apk':'app-debug.apk','aab':'app-debug.aab'}

class Service(ThreadingHTTPServer):
    daemon_threads=True
    def __init__(self, address, token, runner, root):
        self.token=token;self.runner=runner;self.root=root
        self.jobs={};self.lock=threading.Lock()
        super().__init__(address, Handler)
    def gc(self):
        for key,job in list(self.jobs.items()):
            if job['status'] in ('complete','failed') and time.monotonic()-job['seen']>900:
                import shutil
                shutil.rmtree(self.root/key,ignore_errors=True)
                del self.jobs[key]
    def worker(self, key, fmt, permissions):
        d=self.root/key
        try:
            with self.lock:self.jobs[key]['status']='building'
            env={k:os.environ[k] for k in ('PATH','HOME','JAVA_HOME','ANDROID_HOME','GRADLE_USER_HOME','GRADLE_BIN','BUILDER_IMAGE') if k in os.environ}
            subprocess.run([self.runner,str(d/'project.zip'),str(d/'out'),fmt,permissions],
                env=env,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=360,check=True)
            result=d/'out'/FORMATS[fmt]
            if not result.is_file() or result.is_symlink() or result.stat().st_size > MAX:
                raise ValueError('No valid result')
            with self.lock:self.jobs[key]['status']='complete'
        except (OSError,ValueError,subprocess.SubprocessError):
            with self.lock:self.jobs[key]['status']='failed'
        finally:
            (d/'project.zip').unlink(missing_ok=True)
            with self.lock:self.jobs[key]['seen']=time.monotonic()

class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.request.settimeout(25)
    def log_message(self,*_): pass
    def answer(self,status,data):
        b=json.dumps(data,separators=(',',':')).encode()
        self.send_response(status);self.send_header('Content-Type','application/json')
        self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(b)))
        self.end_headers();self.wfile.write(b)
    def auth(self):
        got=self.headers.get('Authorization','')
        if not hmac.compare_digest(got.encode(),('Bearer '+self.server.token).encode()):
            self.answer(401,{'error':'invalid_token'});return False
        return True
    def do_POST(self):
        if not self.auth():return
        if self.path=='/v1/generate':return self.generate()
        m=re.fullmatch(r'/v1/jobs/([a-f0-9]{32})/(apk|aab)',self.path)
        if not m:return self.answer(404,{'error':'not_found'})
        key,fmt=m.groups()
        perms=set(filter(None,self.headers.get('X-Builder-Permissions','').split(',')))
        if not perms<=PERMISSIONS:return self.answer(400,{'error':'permission'})
        permissions=','.join(sorted(perms))
        try:size=int(self.headers.get('Content-Length','-1'))
        except ValueError:size=-1
        if self.headers.get('Transfer-Encoding') or not 0<size<=MAX:return self.answer(413,{'error':'invalid_size'})
        with self.server.lock:
            self.server.gc()
            old=self.server.jobs.get(key)
            if old:
                return self.answer(200 if old['format']==fmt else 409,{'id':key,'status':old['status']})
            if any(j['status'] in ('uploading','queued','building') for j in self.server.jobs.values()) or len(self.server.jobs)>=8:
                return self.answer(429,{'error':'busy'})
            d=self.server.root/key;d.mkdir(mode=0o700)
            self.server.jobs[key]={'status':'uploading','format':fmt,'seen':time.monotonic()}
        try:
            with (d/'project.zip').open('xb') as out:
                remaining=size
                while remaining:
                    buf=self.rfile.read(min(remaining,65536))
                    if not buf:raise IOError('Incomplete request')
                    out.write(buf);remaining-=len(buf)
        except OSError:
            import shutil
            with self.server.lock:
                self.server.jobs.pop(key,None);shutil.rmtree(d,ignore_errors=True)
            return
        with self.server.lock:self.server.jobs[key]['status']='queued'
        threading.Thread(target=self.server.worker,args=(key,fmt,permissions),daemon=True).start()
        return self.answer(202,{'id':key,'status':'queued'})
    def generate(self):
        if not os.environ.get('OPENAI_API_KEY') or not os.environ.get('BUILDER_AI_MODEL'):
            return self.answer(503,{'error':'ai_not_configured'})
        try:size=int(self.headers.get('Content-Length','-1'))
        except ValueError:size=-1
        if not 0<size<=12000 or self.headers.get('Transfer-Encoding'):return self.answer(413,{'error':'invalid_size'})
        try:
            data=json.loads(self.rfile.read(size))
            prompt=data['prompt']
            if not isinstance(prompt,str) or not 5<=len(prompt.strip())<=2400:raise ValueError()
        except (OSError,ValueError,KeyError,TypeError):return self.answer(400,{'error':'invalid_prompt'})
        with self.server.lock:
            now=time.monotonic()
            if now-getattr(self.server,'last_ai',-1000)<30:return self.answer(429,{'error':'busy'})
            self.server.last_ai=now
        try:html=generate_html(prompt)
        except (OSError,ValueError,KeyError):return self.answer(502,{'error':'ai_error'})
        return self.answer(200,{'html':html})
    def do_GET(self):
        if not self.auth():return
        m=re.fullmatch(r'/v1/jobs/([a-f0-9]{32})(/file)?',self.path)
        if not m:return self.answer(404,{'error':'not_found'})
        key,is_file=m.groups()
        with self.server.lock:
            self.server.gc()
            job=self.server.jobs.get(key)
            if not job:return self.answer(404,{'error':'not_found'})
            status,fmt=job['status'],job['format']
            if not is_file: return self.answer(200,{'id':key,'status':status,'format':fmt})
            if status!='complete':return self.answer(409,{'error':'not_ready'})
            f=(self.server.root/key/'out'/FORMATS[fmt]).open('rb')
        with f:
            self.send_response(200);self.send_header('Content-Type','application/octet-stream')
            self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(os.fstat(f.fileno()).st_size))
            self.end_headers()
            import shutil
            shutil.copyfileobj(f,self.wfile)

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--runner',type=pathlib.Path,required=True)
    p.add_argument('--port',type=int,default=8077)
    a=p.parse_args()
    token=os.environ.get('BUILDER_AUTH_TOKEN','')
    if len(token)<32 or not re.fullmatch(r'[A-Za-z0-9_-]+',token):p.error('Set a strong BUILDER_AUTH_TOKEN')
    with tempfile.TemporaryDirectory(prefix='private-builder-') as d:
        with Service(('127.0.0.1',a.port),token,str(a.runner.resolve(strict=True)),pathlib.Path(d)) as s:
            s.serve_forever()
if __name__=='__main__':main()
