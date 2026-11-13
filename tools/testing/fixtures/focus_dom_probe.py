import sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import test_prototype_care_android as web
web.forward()
print(web.dom("""JSON.stringify((()=>{
const svg=document.querySelector('svg');return {url:location.href,w:innerWidth,h:innerHeight,
svg:svg?.getBoundingClientRect().toJSON(),body:document.body.getBoundingClientRect().toJSON(),
display:svg&&getComputedStyle(svg).display,visibility:svg&&getComputedStyle(svg).visibility,
opacity:svg&&getComputedStyle(svg).opacity,children:svg?.children.length,
rect:document.querySelector('rect')?.getBoundingClientRect().toJSON()};})())"""))
