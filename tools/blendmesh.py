import sys, struct, numpy as np
sys.path.insert(0,'.')
from blend import Blend
b=Blend('/mnt/user-data/attachments/b3729478-f50d-52e0-a0cc-895699bb2667/27e69337-n___animacja.blend')
def idname(stype,a):
    off=b.field(stype,'id')[2]+b.field('ID','name')[2]
    return b.d[a+off:a+off+64].split(b'\0')[0].decode()
def arr(ptr, stype):
    bl=b.deref(ptr); return bl
def mesh(addr):
    tv=b.get('Mesh',addr,'totvert'); tp=b.get('Mesh',addr,'totpoly'); tl=b.get('Mesh',addr,'totloop')
    vb=b.deref(b.get('Mesh',addr,'mvert')); lb=b.deref(b.get('Mesh',addr,'mloop')); pb=b.deref(b.get('Mesh',addr,'mpoly'))
    vs=b.tlen['MVert']; co=b.field('MVert','co')[2]
    V=np.array([struct.unpack_from('<3f',b.d,vb[5]+i*vs+co) for i in range(tv)])
    ls=b.tlen['MLoop']; lv=b.field('MLoop','v')[2]
    L=[struct.unpack_from('<I',b.d,lb[5]+i*ls+lv)[0] for i in range(tl)]
    ps=b.tlen['MPoly']; lst=b.field('MPoly','loopstart')[2]; tot=b.field('MPoly','totloop')[2]; mat=b.field('MPoly','mat_nr')[2]
    P=[(struct.unpack_from('<i',b.d,pb[5]+i*ps+lst)[0],struct.unpack_from('<i',b.d,pb[5]+i*ps+tot)[0],struct.unpack_from('<h',b.d,pb[5]+i*ps+mat)[0]) for i in range(tp)]
    uvp=b.get('Mesh',addr,'mloopuv'); UV=None
    if uvp:
        ub=b.deref(uvp); us=b.tlen['MLoopUV']; uo=b.field('MLoopUV','uv')[2]
        UV=np.array([struct.unpack_from('<2f',b.d,ub[5]+i*us+uo) for i in range(tl)])
    # materials
    tc=b.get('Mesh',addr,'totcol'); mats=[]
    mp=b.get('Mesh',addr,'mat')
    if mp and tc:
        mb=b.deref(mp)
        for i in range(tc):
            ma=struct.unpack_from('<Q',b.d,mb[5]+8*i)[0]; mab=b.deref(ma)
            if mab:
                a=mab[5]; mats.append((idname('Material',a), b.get('Material',a,'r'),b.get('Material',a,'g'),b.get('Material',a,'b'),b.get('Material',a,'metallic'),b.get('Material',a,'roughness')))
    return V,L,P,UV,mats
objs={}
for bl in b.blocks_of('Object'):
    a=bl[5]; nm=idname('Object',a)
    data=b.deref(b.get('Object',a,'data'))
    if not data or b.sidx[data[3]]!='Mesh': continue
    M=np.array(b.get('Object',a,'obmat',fmt='16f')).reshape(4,4).T
    par=b.get('Object',a,'parent')
    objs[nm]=dict(addr=a,ptr=bl[2],mesh=mesh(data[5]),M=M,parent=par,loc=b.get('Object',a,'loc'),rot=b.get('Object',a,'rot'),size=b.get('Object',a,'size'),adt=b.get('Object',a,'adt'))
ptr2name={o['ptr']:n for n,o in objs.items()}
def listbase(addr_first):
    out=[]; p=addr_first
    while p:
        bl=b.deref(p)
        if not bl: break
        out.append(bl); p=struct.unpack_from('<Q',b.d,bl[5])[0]
    return out
def action_of(o):
    if not o['adt']: return None
    ab=b.deref(o['adt']); act=b.get('AnimData',ab[5],'action')
    return b.deref(act)
def fcurves(actbl):
    a=actbl[5]; first=b.get('bAction',a,'curves',fmt='QQ')[0]
    out=[]
    for fb in listbase(first):
        f=fb[5]
        path=b.deref(b.get('FCurve',f,'rna_path')); path=b.d[path[5]:path[5]+64].split(b'\0')[0].decode() if path else ''
        idx=b.get('FCurve',f,'array_index'); n=b.get('FCurve',f,'totvert')
        bz=b.deref(b.get('FCurve',f,'bezt')); bs=b.tlen['BezTriple']; vo=b.field('BezTriple','vec')[2]
        ipo_off=b.field('BezTriple','ipo')[2]
        keys=[]
        for i in range(n):
            v=struct.unpack_from('<9f',b.d,bz[5]+i*bs+vo)
            keys.append((v[3],v[4],v[0],v[1],v[6],v[7], b.d[bz[5]+i*bs+ipo_off]))
        out.append((path,idx,keys))
    return out
