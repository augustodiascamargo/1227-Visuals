import sys, struct, gzip, numpy as np
sys.path.insert(0,'.')
from blendmesh import *
def ease(t): t=min(1,max(0,t)); return t*t*(3-2*t)
def evalc(keys,f):
    if f<=keys[0][0]: return keys[0][1]
    for i in range(len(keys)-1):
        a,b2=keys[i],keys[i+1]
        if a[0]<=f<=b2[0]:
            t=(f-a[0])/(b2[0]-a[0]); return a[1]+(b2[1]-a[1])*ease(t)
    return keys[-1][1]
def rot(e):
    x,y,z=e
    Rx=np.array([[1,0,0],[0,np.cos(x),-np.sin(x)],[0,np.sin(x),np.cos(x)]])
    Ry=np.array([[np.cos(y),0,np.sin(y)],[0,1,0],[-np.sin(y),0,np.cos(y)]])
    Rz=np.array([[np.cos(z),-np.sin(z),0],[np.sin(z),np.cos(z),0],[0,0,1]])
    return Rz@Ry@Rx
order=['OBCylinder','OBPlane.001','OBPlane.002','OBSphere']
curves={n:fcurves(action_of(objs[n])) for n in order}
def local(n,f):
    o=objs[n]; loc=list(o['loc']); r=list(o['rot']); s=list(o['size'])
    for path,idx,keys in curves[n]:
        v=evalc(keys,f)
        if path=='location': loc[idx]=v
        elif path=='rotation_euler': r[idx]=v
        elif path=='scale': s[idx]=v
    M=np.eye(4); M[:3,:3]=rot(r)@np.diag(s); M[:3,3]=loc; return M
def world(n,f):
    o=objs[n]; L=local(n,f)
    if o['parent']:
        pn=ptr2name[o['parent']]; PI=np.array(b.get('Object',o['addr'],'parentinv',fmt='16f')).reshape(4,4).T
        return world(pn,f)@PI@L
    return L
frames=list(range(1,66))
# normalise to the open pose at the last frame: knife axis along -x (blade tip) ... keep blender coords, centre at handle pivot
pivot=world('OBPlane.002',65)[:3,3]
parts=[]
for n in order:
    V,L,P,UV,mats=objs[n]['mesh']
    tris=[]; tmat=[]
    for ls,tl,mat in P:
        idx=[L[ls+k] for k in range(tl)]
        for k in range(1,tl-1): tris.append((idx[0],idx[k],idx[k+1])); tmat.append(mat)
    tris=np.array(tris)
    # smooth normals in object space
    N=np.zeros_like(V)
    fn=np.cross(V[tris[:,1]]-V[tris[:,0]],V[tris[:,2]]-V[tris[:,0]])
    for k in range(3): np.add.at(N,tris[:,k],fn)
    N/=np.maximum(1e-9,np.linalg.norm(N,axis=1))[:,None]
    parts.append((n,V,N,tris,np.array(tmat)))
    print(n,len(V),len(tris))
mats=[]
for f in frames:
    row=[]
    for n in order:
        W=world(n,f); W[:3,3]-=pivot; row.append(W[:3,:].astype('>f4'))
    mats.append(row)
# verify open/closed extents
for f in (1,25,40,65):
    pts=[]
    for (n,V,N,T,M) in parts:
        W=world(n,f); pts.append((np.hstack([V,np.ones((len(V),1))])@W.T)[:,:3]-pivot)
    pts=np.vstack(pts); print('frame',f,'bbox',pts.min(0).round(2),pts.max(0).round(2))
with gzip.open(sys.argv[1],'wb') as g:
    g.write(struct.pack('>4sIIf',b'K127',len(parts),len(frames),24.0))
    for (n,V,N,T,M) in parts:
        g.write(struct.pack('>II',len(V),len(T)))
        g.write(V.astype('>f4').tobytes()); g.write(N.astype('>f4').tobytes())
        g.write(T.astype('>u2').tobytes()); g.write(M.astype('>u1').tobytes())
    for row in mats:
        for m in row: g.write(m.tobytes())
