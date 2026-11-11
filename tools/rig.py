import sys, numpy as np
sys.path.insert(0,'.')
from fbx import parse, find
SEC=46186158000
def props(n):
    p=find(n,'Properties70')
    return {q[1][0]:q[1][4:] for q in (find(p[0],'P') if p else [])}
def rot(deg):
    x,y,z=np.radians(deg)
    Rx=np.array([[1,0,0],[0,np.cos(x),-np.sin(x)],[0,np.sin(x),np.cos(x)]])
    Ry=np.array([[np.cos(y),0,np.sin(y)],[0,1,0],[-np.sin(y),0,np.cos(y)]])
    Rz=np.array([[np.cos(z),-np.sin(z),0],[np.sin(z),np.cos(z),0],[0,0,1]])
    return Rz@Ry@Rx
def trs(t,r,s,pre=(0,0,0)):
    M=np.eye(4); M[:3,:3]=rot(pre)@rot(r)@np.diag(s); M[:3,3]=t; return M
class Rig:
    def __init__(self,path):
        nodes=parse(path)
        objs=[n for n in nodes if n[0]=='Objects'][0]; conns=[n for n in nodes if n[0]=='Connections'][0]
        self.objs={c[1][0]:c for c in objs[2]}
        self.models={k:v for k,v in self.objs.items() if v[0]=='Model'}
        self.name={k:v[1][1].split('\x00')[0] for k,v in self.objs.items() if len(v[1])>1 and isinstance(v[1][1],str)}
        self.parent={}; self.op=[]
        for c in conns[2]:
            if c[1][0]=='OO':
                a,b=c[1][1],c[1][2]
                if a in self.models and (b in self.models or b==0): self.parent[a]=b
                self.op.append((a,b,None))
            elif c[1][0]=='OP': self.op.append((c[1][1],c[1][2],c[1][3]))
        self.p={k:props(v) for k,v in self.models.items()}
        self.byname={self.name[k]:k for k in self.models}
        # animation: curve nodes per (model, prop) per layer
        layers={k:self.name[k] for k,v in self.objs.items() if v[0]=='AnimationLayer'}
        cn_layer={}
        for a,b,_ in self.op:
            if a in self.objs and self.objs[a][0]=='AnimationCurveNode' and b in layers: cn_layer[a]=layers[b]
        self.anim={}
        for a,b,pn in self.op:
            if pn and a in self.objs and self.objs[a][0]=='AnimationCurveNode' and b in self.models:
                self.anim.setdefault(cn_layer.get(a),{})[(b,pn)]=a
        self.curves={}
        for a,b,pn in self.op:
            if pn and a in self.objs and self.objs[a][0]=='AnimationCurve':
                c=self.objs[a]
                kt=np.array(find(c,'KeyTime')[0][1][0],dtype=np.float64)/SEC
                kv=np.array(find(c,'KeyValueFloat')[0][1][0],dtype=np.float64)
                self.curves.setdefault(b,{})[pn]=(kt,kv)
    def val(self,layer,mid,prop,t):
        base=list(self.p[mid].get(prop,[0,0,0] if prop!='Lcl Scaling' else [1,1,1]))
        cn=self.anim.get(layer,{}).get((mid,prop))
        if cn is None: return base
        cs=self.curves.get(cn,{})
        for i,ax in enumerate('XYZ'):
            k=cs.get('d|'+ax)
            if k: base[i]=float(np.interp(t,k[0],k[1]))
        return base
    def local(self,mid,layer,t,extra=None):
        T=self.val(layer,mid,'Lcl Translation',t); R=self.val(layer,mid,'Lcl Rotation',t); S=self.val(layer,mid,'Lcl Scaling',t)
        pre=self.p[mid].get('PreRotation',[0,0,0])
        M=trs(T,R,S,pre)
        if extra is not None and mid in extra:
            M=M@extra[mid]
        return M
    def glob(self,mid,layer,t,extra=None,cache=None):
        if cache is not None and mid in cache: return cache[mid]
        L=self.local(mid,layer,t,extra)
        p=self.parent.get(mid,0)
        G=(self.glob(p,layer,t,extra,cache)@L) if p in self.models else L
        if cache is not None: cache[mid]=G
        return G
    def duration(self,layer):
        mx=0
        for (mid,pn),cn in self.anim.get(layer,{}).items():
            for k in self.curves.get(cn,{}).values(): mx=max(mx,k[0][-1])
        return mx
