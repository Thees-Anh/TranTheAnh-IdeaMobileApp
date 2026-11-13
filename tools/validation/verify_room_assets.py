"""Verify bundled static room geometry against the Android fixed anchor contract."""
import json,re,struct
from pathlib import Path

ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
def main():
    source=(ROOT/'shared/src/androidMain/kotlin/com/pawquest/presentation/scene/RoomAssets.kt').read_text()
    total=0;triangles=0
    for name in ['bed','lamp','carpet']:
        raw=(ROOT/f'shared/src/androidMain/assets/models/furniture/{name}.glb').read_bytes();total+=len(raw)
        magic,version,size=struct.unpack_from('<III',raw);assert magic==0x46546c67 and version==2 and size==len(raw)
        n=struct.unpack_from('<I',raw,12)[0];doc=json.loads(raw[20:20+n])
        assert not doc.get('skins') and not doc.get('animations') and not doc.get('textures')
        assert all('uri' not in b for b in doc['buffers'])
        match=re.search(r'FurnitureSlot\.'+name.upper()+r' -> RoomAnchor\(([^)]*)floatArrayOf\(([^)]*)\)',source)
        anchor=[float(x.strip().rstrip('f')) for x in match[1].rstrip(',').split(',')]
        limit=[float(x.strip().rstrip('f')) for x in match[2].split(',')]
        for mesh in doc['meshes']:
            for primitive in mesh['primitives']:
                a=doc['accessors'][primitive['attributes']['POSITION']];triangles+=a['count']//3
                center=[(a['min'][i]+a['max'][i])/2 for i in range(3)]
                half=[(a['max'][i]-a['min'][i])/2 for i in range(3)]
                assert all(0<=half[i]<=limit[i] for i in range(3))
                assert abs(center[0])<.02 and abs(center[2])<.02 and abs(center[1]-half[1])<.02
                assert anchor[2]+a['max'][2]<=-.12
                assert all(max(abs(anchor[i]+a['min'][i]),abs(anchor[i]+a['max'][i]))<1.37 for i in range(3))
    assert total<100000 and triangles<1000
    print(f'PASS 3 room GLBs: {triangles} triangles, {total} bytes, fixed anchors behind cat and within portrait envelope; no textures/animations/external resources')

if __name__=='__main__': main()
