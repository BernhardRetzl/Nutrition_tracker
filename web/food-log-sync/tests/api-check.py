"""Local-only API integration check; never targets production data."""
import urllib.request, urllib.error, http.cookiejar, json
ROOT='http://127.0.0.1:5179'
opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
opener.open(ROOT+'/signin-with-chatgpt?return_to=/').read()
def req(method='GET',body=None,origin=None):
    headers={'Content-Type':'application/json'}
    if origin:headers['Origin']=origin
    r=urllib.request.Request(ROOT+'/api/sync',data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        with opener.open(r) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as e:return e.code,e.read().decode()
code,initial=req();assert code==200,(code,initial)
s={'recipes':{},'foods':{'test-milk':{'name':'Test milk','active':True,'recipeId':''}},'entries':{'test-milk@2026-11-17':{'foodId':'test-milk','date':'2026-11-17','name':'Test milk','grams':'193'}}}
code,saved=req('POST',{'revision':initial['revision'],'snapshot':s},ROOT);assert code==200,(code,saved)
assert req()[1]['snapshot']==s
assert req('POST',{'revision':initial['revision'],'snapshot':s},ROOT)[0]==409
assert req('POST',{'revision':saved['revision'],'snapshot':s},'https://untrusted.example')[0]==403
assert req('POST',{'revision':saved['revision'],'snapshot':{'foods':{}}},ROOT)[0]==400
assert req('POST',{'revision':saved['revision'],'snapshot':initial['snapshot']},ROOT)[0]==200
try:urllib.request.urlopen(ROOT+'/api/sync');raise AssertionError('Anonymous request accepted')
except urllib.error.HTTPError as e:assert e.code==401
print('API checks passed: authenticated read/write, stale-write protection, cross-origin rejection, input validation, anonymous rejection; local fixture restored.')
