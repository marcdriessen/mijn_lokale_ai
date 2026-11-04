#!/usr/bin/env python3
"""Checks every built-in news source for paywalls: reads the feed, opens a few articles and
reports HTTP status, the amount of readable text and paywall words found.
Usage: python3 tools/bronnencheck.py   (run from the repository root)"""
import json,re,urllib.request,urllib.error,concurrent.futures as cf,html as H
s=open('app/src/main/java/com/example/auralocalai/data/briefing/BriefingModels.kt').read()
u6=s[s.index('val updatesV6'):s.index('val sourcesV6')]
upd=dict(re.findall(r'"([^"]+)" to \("[^"]+" to "([^"]+)"\)',u6))
srcs=[]
for m in re.finditer(r'NewsSource\("([^"]+)",\s*"([^"]+)",\s*(.+)\)',s):
    id,name,rest=m.groups()
    um=re.match(r'"([^"]+)"',rest.strip())
    srcs.append(dict(id=id,name=name,url=upd.get(id,um.group(1) if um else rest),enabled='enabled = false' not in rest))
UA="Mozilla/5.0 (Android) AuraLocalAI-Briefing/1.0"
def get(u):
    try:
        r=urllib.request.urlopen(urllib.request.Request(u,headers={"User-Agent":UA,"Accept-Language":"nl,bg;q=0.8,en;q=0.6"}),timeout=25)
        return r.status,r.read().decode('utf-8','replace'),r.geturl()
    except urllib.error.HTTPError as e:
        return e.code,e.read().decode('utf-8','replace'),u
    except Exception as e:
        return 0,str(e),u
MARK=["isaccessibleforfree\":false","isaccessibleforfree\":\"false\"","content_tier\" content=\"locked","content_tier\" content=\"metered",
"subscribe to read","subscribe to continue","to continue reading","subscribers only","subscriber-only","this article is for subscribers",
"alleen voor abonnees","lees verder met een abonnement","word abonnee","log in om verder te lezen","premium artikel","plus-artikel","alleen voor leden","exclusief voor abonnees",
"само за абонати","абонирайте се","абонирай се","стани абонат","вече сте абонат","абонамент","платено съдържание","достъпно за абонати","paywall","piano.io","tinypass","pianoid","poool","premium"]
def paras(h):
    h=re.sub(r'(?is)<(script|style|nav|header|footer|aside)[^>]*>.*?</\1>',' ',h)
    ps=[H.unescape(re.sub('<[^>]+>','',p)).strip() for p in re.findall(r'(?is)<p[^>]*>(.*?)</p>',h)]
    return sum(len(p) for p in ps if len(p)>60)
def links(feed):
    ls=re.findall(r'<item[^>]*>.*?<link>\s*(?:<!\[CDATA\[)?\s*([^<\]\s]+)',feed,re.S) or re.findall(r'<entry>.*?<link[^>]*href="([^"]+)"',feed,re.S)
    return [H.unescape(l) for l in ls][:4]
def check(src):
    u=src['url']
    if not u.startswith('http') or 'news.google.com' in u: return src['id'],'(Google News / overgeslagen)',[]
    c,feed,_=get(u)
    if c!=200: return src['id'],f'feed {c}',[]
    res=[]
    for l in links(feed):
        c,h,final=get(l)
        low=h.lower().replace(' ','')
        found=sorted({m for m in MARK if m.replace(' ','') in low})
        cf_=('attention required' in h.lower() or 'just a moment' in h.lower())
        res.append((c,paras(h),'CF' if cf_ else '',found,final[:90]))
    return src['id'],'ok',res
with cf.ThreadPoolExecutor(12) as ex:
    for id,st,res in ex.map(check,srcs):
        print(f'== {id}: {st}')
        for r in res: print('  ',r[0],r[1],r[2],r[3],r[4])
