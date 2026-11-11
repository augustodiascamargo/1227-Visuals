import struct, sys, re
class Blend:
    def __init__(self, path):
        d=open(path,'rb').read(); self.d=d
        assert d[:7]==b'BLENDER'
        self.ps=8 if d[7:8]==b'-' else 4; self.le=d[8:9]==b'v'
        e='<' if self.le else '>'; self.e=e
        pos=12; self.blocks=[]; self.byptr={}
        P='Q' if self.ps==8 else 'I'
        while pos<len(d):
            code=d[pos:pos+4]; size,=struct.unpack_from(e+'i',d,pos+4)
            ptr,=struct.unpack_from(e+P,d,pos+8); sdna,count=struct.unpack_from(e+'ii',d,pos+8+self.ps)
            dp=pos+16+self.ps
            b=(code,size,ptr,sdna,count,dp); self.blocks.append(b); self.byptr[ptr]=b
            pos=dp+size
            if code==b'ENDB': break
        dna=[b for b in self.blocks if b[0]==b'DNA1'][0]; p=dna[5]
        def align(x): return (x+3)&~3
        assert d[p:p+4]==b'SDNA'; p+=4
        assert d[p:p+4]==b'NAME'; p+=4; n,=struct.unpack_from(e+'i',d,p); p+=4
        names=[]
        for _ in range(n):
            q=d.index(b'\0',p); names.append(d[p:q].decode()); p=q+1
        p=align(p); assert d[p:p+4]==b'TYPE'; p+=4; n,=struct.unpack_from(e+'i',d,p); p+=4
        types=[]
        for _ in range(n):
            q=d.index(b'\0',p); types.append(d[p:q].decode()); p=q+1
        p=align(p); assert d[p:p+4]==b'TLEN'; p+=4
        tlen=list(struct.unpack_from(e+'%dh'%n,d,p)); p+=2*n
        p=align(p); assert d[p:p+4]==b'STRC'; p+=4; n,=struct.unpack_from(e+'i',d,p); p+=4
        self.structs={}; self.sidx=[]
        for _ in range(n):
            t,nf=struct.unpack_from(e+'hh',d,p); p+=4
            fields=[]; off=0
            for _ in range(nf):
                ft,fn=struct.unpack_from(e+'hh',d,p); p+=4
                name=names[fn]
                if name.startswith('*') or name.startswith('(*'): sz=self.ps
                else: sz=tlen[ft]
                mult=1
                for m in re.findall(r'\[(\d+)\]',name): mult*=int(m)
                fields.append((types[ft],name,off,sz*mult)); off+=sz*mult
            self.structs[types[t]]=fields; self.sidx.append(types[t])
        self.tlen=dict(zip(types,tlen))
    def field(self,stype,name):
        for f in self.structs[stype]:
            base=re.sub(r'[\*\(\)]','',f[1]).split('[')[0]
            if base==name: return f
        raise KeyError(stype+'.'+name)
    def get(self,stype,addr,name,fmt=None):
        t,n,off,sz=self.field(stype,name)
        if n.startswith('*'): return struct.unpack_from(self.e+('Q' if self.ps==8 else 'I'),self.d,addr+off)[0]
        if fmt: return struct.unpack_from(self.e+fmt,self.d,addr+off)
        if t=='char': return self.d[addr+off:addr+off+sz].split(b'\0')[0].decode('latin1')
        f={'int':'i','short':'h','float':'f','char':'b','double':'d','uint':'I'}.get(t)
        cnt=sz//self.tlen[t] if t in self.tlen and self.tlen[t] else 1
        v=struct.unpack_from(self.e+'%d%s'%(cnt,f),self.d,addr+off)
        return v[0] if cnt==1 else v
    def blocks_of(self,stype):
        i=self.sidx.index(stype)
        return [b for b in self.blocks if b[3]==i and b[0] not in (b'DNA1',)]
    def deref(self,ptr): return self.byptr.get(ptr)
