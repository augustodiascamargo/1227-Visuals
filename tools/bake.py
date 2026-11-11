import sys, struct, gzip, numpy as np
sys.path.insert(0,'.')
from rig import *
from fbx import find
r=Rig(sys.argv[1]); out=sys.argv[2]
geo=[v for v in r.objs.values() if v[0]=='Geometry'][0]
V=np.array(find(geo,'Vertices')[0][1][0]).reshape(-1,3)
pvi=find(geo,'PolygonVertexIndex')[0][1][0]
uvl=find(geo,'LayerElementUV')[0]
UV=np.array(find(uvl,'UV')[0][1][0]).reshape(-1,2); UVI=find(uvl,'UVIndex')[0][1][0]
meshid=[k for k,v in r.models.items() if v[1][2]=='Mesh'][0]
Gmesh=r.glob(meshid,None,0,cache={})
# clusters
clusters=[]
for k,v in r.objs.items():
    if v[0]=='Deformer' and v[1][2]=='Cluster':
        ix=find(v,'Indexes')
        if not ix: continue
        bone=[a for a,b,p in r.op if b==k and a in r.models][0]
        T=np.array(find(v,'Transform')[0][1][0]).reshape(4,4).T
        TL=np.array(find(v,'TransformLink')[0][1][0]).reshape(4,4).T
        clusters.append((bone,np.array(ix[0][1][0]),np.array(find(v,'Weights')[0][1][0]),np.linalg.inv(TL)@Gmesh))
print('clusters',len(clusters))
Vh=np.hstack([V,np.ones((len(V),1))])
layer='metarig|Idle'; dur=r.duration(layer)
S=r.glob(r.byname['upper_arm.R'],layer,0,cache={})[:3,3]
arm=r.byname['upper_arm.R']; bat=r.byname['Bat']
def W(theta,pivot):
    c,s=np.cos(np.radians(theta)),np.sin(np.radians(theta))
    R=np.array([[1,0,0],[0,c,-s],[0,s,c]])
    M=np.eye(4); M[:3,:3]=R; M[:3,3]=pivot-R@pivot; return M
def ease(t): t=min(1,max(0,t)); return t*t*(3-2*t)
def theta_at(t):
    # three swings: wind up over the top, smash forward-down, recover
    for i,(t0,big) in enumerate([(0.35,False),(0.85,False),(1.35,True)]):
        if t0<=t<t0+0.5:
            u=t-t0
            if u<0.24: return 150*ease(u/0.24)*(1.15 if big else 1)
            if u<0.36: k=(u-0.24)/0.12; a=150*(1.15 if big else 1); return a+(330-a)*k*k
            return 330+30*ease((u-0.36)/0.14)
    return 0.0
fps=24; total=2.6; n=int(total*fps)+1
frames=[]; tips=[]
for f in range(n):
    t=f/fps
    th=theta_at(t)
    cache={}
    # evaluate globals with the swing applied to the right arm chain and the bat
    def glob(mid):
        if mid in cache: return cache[mid]
        L=r.local(mid,layer,t%dur)
        p=r.parent.get(mid,0)
        G=(glob(p)@L) if p in r.models else L
        if mid in (arm,bat) and th!=0: G=W(th,S)@G
        cache[mid]=G; return G
    acc=np.zeros((len(V),3)); wsum=np.zeros(len(V))
    for bone,ix,w,bind in clusters:
        M=glob(bone)@bind
        acc[ix]+= (Vh[ix]@M.T)[:,:3]*w[:,None]; wsum[ix]+=w
    rest=(Vh@r.glob(meshid,layer,0,cache={}).T)[:,:3]
    m=wsum>1e-6
    P=rest.copy(); P[m]=acc[m]/wsum[m][:,None]
    frames.append(P)
    G=glob(bat); tips.append((G@np.array([0,1,0,1]))[:3])
frames=np.array(frames); np.save("frames_raw.npy",frames); np.save("tips.npy",np.array(tips))
# normalise: feet on y=0, centred in x/z at bind, height -> 1 unit (scaled in Java)
mn=frames[0].min(0); mx=frames[0].max(0); h=mx[1]-mn[1]
cx=(mn[0]+mx[0])/2; cz=(mn[2]+mx[2])/2
frames=(frames-np.array([cx,mn[1],cz]))/h
print('height cm',h,'frames',frames.shape)
lo=frames.min((0,1)); hi=frames.max((0,1))
q=np.round((frames-lo)/(hi-lo)*65535-32768).astype('<i2')
# triangulate + unique corners
corners={}; cpos=[]; cuv=[]; tris=[]
poly=[]; pv=0
for i,ix in enumerate(pvi):
    last=ix<0; vi=~ix if last else ix
    key=(vi,UVI[i])
    if key not in corners:
        corners[key]=len(cpos); cpos.append(vi); cuv.append(UV[UVI[i]])
    poly.append(corners[key])
    if last:
        for k in range(1,len(poly)-1): tris+= [poly[0],poly[k],poly[k+1]]
        poly=[]
print('corners',len(cpos),'tris',len(tris)//3)
with gzip.open(out,'wb') as g:
    g.write(struct.pack('>4sIIII',b'M127',len(V),len(cpos),len(tris),n))
    g.write(struct.pack('>f',fps))
    g.write(struct.pack('>6f',*lo,*hi))
    g.write(np.array(cpos,dtype='>u2').tobytes())
    g.write(np.array(cuv,dtype='>f4').tobytes())
    g.write(np.array(tris,dtype='>u2').tobytes())
    g.write(q.astype('>i2').tobytes())
print('strike tip (normalised) at 1.71s:', (tips[int(1.71*fps)]-np.array([cx,mn[1],cz]))/h)
for tt in (0.71,1.21,1.71): print(tt, np.round((tips[int(tt*fps)]-np.array([cx,mn[1],cz]))/h,2))
