"""Generate shared artwork/slot registry and isolated thumbnail documents."""
from pathlib import Path
import json,xml.etree.ElementTree as E
from decor_stage import ITEMS,ART,NS
ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file());E.register_namespace('',NS)
package=ROOT/'shared/src/commonMain/kotlin/com/pawquest/domain/model/DecorCatalog.kt'
text='package com.pawquest.domain.model\n\n/** Generated from design/assets/decor/catalog.json; prices always come from the server. */\nobject DecorCatalog {\n'
for category,name,enum in [('ACCESSORY','equipmentSlots','EquipmentSlot'),('FURNITURE','furnitureSlots','FurnitureSlot')]:
 text+=f'    val {name}=mapOf(\n'+',\n'.join(f'        "{i["code"]}" to {enum}.{i["slot"]}' for i in ITEMS if i['category']==category)+'\n    )\n'
text+='    val assetIds=setOf('+','.join(json.dumps(i['id']) for i in ITEMS)+')\n'
text+='    val supportedAssets=assetIds+setOf("item.accessory.hat","item.accessory.glasses","item.accessory.collar","item.furniture.bed","item.furniture.lamp","item.furniture.carpet")\n}\n'
package.write_text(text,encoding='utf-8')
ui='''package com.pawquest.presentation.components
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import com.pawquest.resources.*
@Composable fun ItemArtwork(assetId: String,imageVector: ImageVector,contentDescription: String?,tint: Color,modifier: Modifier=Modifier) {
 val art=when(assetId) {
'''
ui+='\n'.join(f'  "{i["id"]}"->Res.drawable.decor_{i["code"]}' for i in ITEMS)
ui+='''
  else->null
 }
 if(art!=null) Image(painterResource(art),contentDescription,modifier,contentScale=ContentScale.Fit)
 else Icon(imageVector,contentDescription,modifier,tint=tint)
}
'''
(ROOT/'shared/src/commonMain/kotlin/com/pawquest/presentation/components/ItemArtwork.kt').write_text(ui,encoding='utf-8')
folder=ROOT/'build/decor-thumbnails';folder.mkdir(parents=True,exist_ok=True)
css=(ROOT/'design/assets/decor/animations.css').read_text(encoding='utf-8')
for i in ITEMS:
 svg=E.Element('{'+NS+'}svg',{'xmlns':NS,'viewBox':'0 0 600 440'})
 # ElementTree emits xmlns automatically; use a parsed root to avoid duplicate attrs.
 svg=E.fromstring(f'<svg xmlns="{NS}" viewBox="0 0 600 440"/>')
 art=E.fromstring(E.tostring(ART[i['id']],encoding='unicode'));art.attrib.pop('class',None);svg.append(art)
 page='<html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\';style-src \'unsafe-inline\'"><style>'+css+'html,body{margin:0;background:#FFF7FB}svg{width:224px;height:192px}*{animation:none!important;transition:none!important}</style></head><body>'+E.tostring(svg,encoding='unicode')+'</body></html>'
 (folder/(i['code']+'.html')).write_text(page,encoding='utf-8')
print('Generated 20 thumbnail sources and shared item artwork registry')
