"""Render static portraits from shipped SVGs with local headless Chrome, no CDN."""
from pathlib import Path
import re,subprocess,xml.etree.ElementTree as E
E.register_namespace('','http://www.w3.org/2000/svg')
ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
OUT=ROOT/'shared/src/commonMain/composeResources/drawable';OUT.mkdir(exist_ok=True)
WORK=ROOT/'build/pet-portraits';WORK.mkdir(parents=True,exist_ok=True)
for key,asset,prefix in [('mochi','prototype','cat'),('puppy','puppy','dog'),('capybara','capybara','capy'),('rabbit','rabbit','bunny')]:
    raw=(ROOT/f'shared/src/androidMain/assets/pet/{asset}_stage.html').read_text(encoding='utf-8')
    tree=E.fromstring(re.search(r'<svg[\s\S]*?</svg>',raw)[0])
    whole=tree.find(f".//*[@id='{prefix}-whole']")
    assert whole is not None
    svg=E.Element('{http://www.w3.org/2000/svg}svg',{'viewBox':'0 0 400 330','class':'state-idle'})
    for child in tree:
        if child.tag.endswith('defs'):svg.append(child)
    svg.append(whole)
    css=re.search(r'<style>([\s\S]*?)</style>',raw)[1]
    html='<html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\';style-src \'unsafe-inline\'"><style>'+css+'html,body{margin:0;background:#FFF7FB}svg{width:800px!important;height:660px!important;aspect-ratio:auto!important}*{animation:none!important;transition:none!important}.hidden{display:none!important}</style></head><body>'+E.tostring(svg,encoding='unicode')+'</body></html>'
    page=WORK/(key+'.html');page.write_text(html,encoding='utf-8')
    result=subprocess.run(['C:/Program Files/Google/Chrome/Application/chrome.exe','--headless','--disable-gpu','--no-first-run','--hide-scrollbars','--force-device-scale-factor=1','--window-size=800,660','--virtual-time-budget=1000','--user-data-dir='+str(WORK/'chrome-profile'),'--screenshot='+str(OUT/('pet_'+key+'.png')),page.as_uri()],capture_output=True,timeout=40)
    assert result.returncode==0 and (OUT/('pet_'+key+'.png')).stat().st_size>2000,key
    print('PASS portrait',key,flush=True)
