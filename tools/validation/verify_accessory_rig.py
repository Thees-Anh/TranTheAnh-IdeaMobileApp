"""Check the actual development rig and rotation-invariant accessory envelopes."""
import hashlib,json,math,struct
from pathlib import Path

ASSETS=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())/'shared/src/androidMain/assets/models'
def glb(path):
    b=path.read_bytes(); magic,version,length=struct.unpack_from('<III',b)
    assert magic==0x46546c67 and version==2 and length==len(b)
    n=struct.unpack_from('<I',b,12)[0]
    return json.loads(b[20:20+n]),b[28+n:]
def values(doc,blob,index):
    a=doc['accessors'][index];v=doc['bufferViews'][a['bufferView']]
    width={'VEC3':3,'VEC4':4,'SCALAR':1}[a['type']]
    offset=v.get('byteOffset',0)+a.get('byteOffset',0)
    return [struct.unpack_from('<'+'f'*width,blob,offset+i*width*4) for i in range(a['count'])]

def main():
    cat,b=glb(ASSETS/'cat.glb')
    assert not cat.get('skins'), 'Replacement skins require explicit socket/bone configuration'
    pivot=next(i for i,n in enumerate(cat['nodes']) if n.get('name')=='Animation pivot')
    geometry=next(i for i,n in enumerate(cat['nodes']) if n.get('name')=='Centered geometry')
    assert geometry in cat['nodes'][pivot]['children']
    assert cat['nodes'][geometry]['translation']==[0,-1.23,0]
    assert len(cat['animations'])==5
    for clip in cat['animations']:
        for channel in clip['channels']:
            assert channel['target']['node']==pivot
            sampler=clip['samplers'][channel['sampler']]
            assert sampler['interpolation']=='LINEAR'
            data=values(cat,b,sampler['output'])
            if channel['target']['path']=='scale':
                assert all(abs(x-y)<1e-6 and abs(y-z)<1e-6 and 0<x<=1.025001 for x,y,z in data)
            if channel['target']['path']=='translation':
                assert all(math.sqrt(x*x+(y-1.23)**2+z*z)<=.160001 for x,y,z in data)
    fingerprint=hashlib.sha256((ASSETS/'cat.glb').read_bytes()).hexdigest()
    count=0
    for path in sorted((ASSETS/'accessories').glob('*.glb')):
        doc,blob=glb(path);meta=json.loads(path.with_suffix('.attachment.json').read_text())
        assert meta['cat_sha256']==fingerprint and meta['parent']=='Centered geometry'
        for mesh in doc['meshes']:
            for primitive in mesh['primitives']:
                for vertex in values(doc,blob,primitive['attributes']['POSITION']):
                    radius=math.sqrt(sum((vertex[k]+meta['position'][k]-(1.23 if k==1 else 0))**2 for k in range(3)))*1.025+.16
                    # Any interpolated rotation preserves radius; uniform scale and translation
                    # extrema bound every pose, not only sampled keyframes.
                    assert all(meta['min'][k]<= (1.23 if k==1 else 0)-radius and
                        meta['max'][k]>= (1.23 if k==1 else 0)+radius for k in range(3))
        count+=1
    assert count==3
    print('PASS actual cat: no skins, rigid Animation pivot hierarchy, 5 bounded clips; all 3 accessory vertex envelopes cover every interpolated pose')

if __name__=='__main__': main()
