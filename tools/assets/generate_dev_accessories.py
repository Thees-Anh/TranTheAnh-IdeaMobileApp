"""Original development GLBs for the inspected, rigid PawQuest cat (stdlib only)."""
import hashlib
import json
import math
from pathlib import Path
import struct

ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
ASSETS=ROOT/'shared/src/androidMain/assets/models'

def generate(name,slot,origin,color,build,folder='accessories',attachment=True):
    vertices=[]
    def tri(a,b,c): vertices.extend([a,b,c])
    def surface(fn,nu=32,nv=8):
        for u in range(nu):
            for v in range(nv):
                a,b,c,d=[fn(i/nu*2*math.pi,j/nv*2*math.pi) for i,j in [(u,v),(u+1,v),(u+1,v+1),(u,v+1)]]
                tri(a,b,c); tri(a,c,d)
    def ring(cx,cy,rx,ry,tube):
        surface(lambda u,v:(cx+(rx+tube*math.cos(v))*math.cos(u),cy+(ry+tube*math.cos(v))*math.sin(u),tube*math.sin(v)))
    def cylinder(radius,y,height):
        for i in range(32):
            a,b=i*math.tau/32,(i+1)*math.tau/32
            p,q=(radius*math.cos(a),y,radius*math.sin(a)),(radius*math.cos(b),y,radius*math.sin(b))
            r,s=(q[0],y+height,q[2]),(p[0],y+height,p[2])
            tri(p,q,r);tri(p,r,s);tri((0,y,0),q,p);tri((0,y+height,0),s,r)
    def box(center,half):
        pts=[tuple(center[k]+half[k]*sign[k] for k in range(3)) for sign in
             [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
        for a,b,c,d in [(0,1,2,3),(4,7,6,5),(0,4,5,1),(3,2,6,7),(0,3,7,4),(1,5,6,2)]:
            tri(pts[a],pts[b],pts[c]);tri(pts[a],pts[c],pts[d])
    build(surface,ring,cylinder,box)
    normals=[]
    for i in range(0,len(vertices),3):
        a,b,c=vertices[i:i+3];u=[b[k]-a[k] for k in range(3)];v=[c[k]-a[k] for k in range(3)]
        n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
        length=math.sqrt(sum(x*x for x in n)) or 1
        normals.extend([tuple(x/length for x in n)]*3)
    blob=bytearray();views=[];accessors=[]
    for values in [vertices,normals]:
        offset=len(blob);blob.extend(struct.pack('<'+'f'*len(values)*3,*[x for row in values for x in row]))
        views.append(dict(buffer=0,byteOffset=offset,byteLength=len(blob)-offset,target=34962))
        accessors.append(dict(bufferView=len(views)-1,componentType=5126,count=len(values),type='VEC3',
            min=[min(p[k] for p in values) for k in range(3)],max=[max(p[k] for p in values) for k in range(3)]))
    doc=dict(asset=dict(version='2.0',generator='PawQuest original development accessories'),scene=0,
        scenes=[dict(nodes=[0])],nodes=[dict(name=name,mesh=0)],
        meshes=[dict(primitives=[dict(attributes=dict(POSITION=0,NORMAL=1),material=0)])],
        materials=[dict(doubleSided=True,pbrMetallicRoughness=dict(baseColorFactor=color,metallicFactor=0,roughnessFactor=.65))],
        buffers=[dict(byteLength=len(blob))],bufferViews=views,accessors=accessors)
    encoded=json.dumps(doc,separators=(',',':')).encode();encoded+=b' '*(-len(encoded)%4)
    path=ASSETS/folder/f'{name}.glb';path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(struct.pack('<III',0x46546c67,2,28+len(encoded)+len(blob))+
        struct.pack('<II',len(encoded),0x4e4f534a)+encoded+struct.pack('<II',len(blob),0x004e4942)+blob)
    if not attachment:
        print('Generated',path.name,len(vertices),'vertices');return
    # Exact cat clips use uniform scales <=1.025 and pivot translation <=0.16.
    # A rotation-invariant sphere contains every vertex at every intermediate pose.
    radius=max(math.sqrt(sum((v[k]+origin[k]-(1.23 if k==1 else 0))**2 for k in range(3))) for v in vertices)*1.025+.16+.05
    meta=dict(slot=slot,cat_sha256=hashlib.sha256((ASSETS/'cat.glb').read_bytes()).hexdigest(),
        parent='Centered geometry',position=origin,
        min=[-radius,1.23-radius,-radius],max=[radius,1.23+radius,radius])
    path.with_suffix('.attachment.json').write_text(json.dumps(meta,indent=2)+'\n')
    print('Generated',path.name,len(vertices),'vertices')

def main():
    generate('hat','HEAD',[0,2.28,.06],[.12,.50,.95,1],lambda s,r,c,b:(c(.32,0,.045),c(.23,.045,.25)))
    generate('glasses','FACE',[0,1.83,.56],[.035,.06,.10,1],lambda s,r,c,b:(r(-.25,0,.125,.17,.025),r(.25,0,.125,.17,.025),b((0,0,0),(.10,.018,.018))))
    generate('collar','NECK',[0,1.31,0],[.95,.18,.20,1],lambda s,r,c,b:s(lambda u,v:((.50+.035*math.cos(v))*math.cos(u),.035*math.sin(v),(.43+.035*math.cos(v))*math.sin(u))))

if __name__=='__main__': main()
