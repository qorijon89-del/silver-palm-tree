"""Optional OpenAI adapter. Disabled unless operator provides model and API key."""
import json, os, re
from urllib.request import Request, urlopen
class NoRedirect(__import__('urllib.request',fromlist=['HTTPRedirectHandler']).HTTPRedirectHandler):
    def redirect_request(self,*_): return None
def generate_html(prompt):
    from urllib.request import build_opener
    body={'model':os.environ['BUILDER_AI_MODEL'],'store':False,'max_output_tokens':7500,'input':[
        {'role':'developer','content':'Create one self-contained offline HTML app in the language of the request. Return only valid HTML in the schema. Use semantic controls, accessible labels, visible focus and aria-live feedback. Include CSS and scripts inline. Do not use external resources, network requests, geolocation, camera, microphone, data collection, login capture or unsafe/illegal functionality.'},
        {'role':'user','content':prompt}],
        'text':{'format':{'type':'json_schema','name':'html_app','strict':True,'schema':{'type':'object','additionalProperties':False,'properties':{'html':{'type':'string'}},'required':['html']}}}}
    req=Request('https://api.openai.com/v1/responses',data=json.dumps(body).encode(),headers={'Authorization':'Bearer '+os.environ['OPENAI_API_KEY'],'Content-Type':'application/json'},method='POST')
    with build_opener(NoRedirect()).open(req,timeout=70) as r:
        payload=json.loads(r.read(512*1024))
    if payload.get('status')!='completed':raise ValueError('incomplete')
    chunks=[c['text'] for part in payload.get('output',[]) if part.get('type')=='message' for c in part.get('content',[]) if c.get('type')=='output_text']
    html=json.loads(''.join(chunks))['html']
    if not isinstance(html,str) or len(html.encode())>220000 or not re.search(r'<html(?:\s|>)',html,re.I):raise ValueError('invalid html')
    return html
