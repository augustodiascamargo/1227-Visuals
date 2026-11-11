import struct, zlib, sys
def parse(path):
    d=open(path,'rb').read()
    ver=struct.unpack_from('<I',d,23)[0]
    big = ver>=7500
    pos=27
    def read_node(pos):
        if big:
            end,nprops,plen=struct.unpack_from('<QQQ',d,pos); pos+=24
        else:
            end,nprops,plen=struct.unpack_from('<III',d,pos); pos+=12
        nl=d[pos]; pos+=1
        name=d[pos:pos+nl].decode('latin1'); pos+=nl
        if end==0: return None,pos
        props=[]
        for _ in range(nprops):
            t=chr(d[pos]); pos+=1
            if t=='Y': props.append(struct.unpack_from('<h',d,pos)[0]); pos+=2
            elif t=='C': props.append(d[pos]); pos+=1
            elif t=='I': props.append(struct.unpack_from('<i',d,pos)[0]); pos+=4
            elif t=='F': props.append(struct.unpack_from('<f',d,pos)[0]); pos+=4
            elif t=='D': props.append(struct.unpack_from('<d',d,pos)[0]); pos+=8
            elif t=='L': props.append(struct.unpack_from('<q',d,pos)[0]); pos+=8
            elif t in 'fdlib':
                n,enc,cl=struct.unpack_from('<III',d,pos); pos+=12
                raw=d[pos:pos+cl]; pos+=cl
                if enc==1: raw=zlib.decompress(raw)
                fmt={'f':'f','d':'d','l':'q','i':'i','b':'b'}[t]
                props.append(list(struct.unpack('<%d%s'%(n,fmt),raw)))
            elif t in 'SR':
                n=struct.unpack_from('<I',d,pos)[0]; pos+=4
                v=d[pos:pos+n]; pos+=n
                props.append(v.decode('latin1') if t=='S' else v)
        children=[]
        while pos<end:
            c,pos=read_node(pos)
            if c is None: break
            children.append(c)
        return (name,props,children),end
    nodes=[]
    while pos<len(d)-160:
        n,pos=read_node(pos)
        if n is None: break
        nodes.append(n)
    return nodes
def find(n,name): return [c for c in n[2] if c[0]==name]
if __name__=='__main__':
    nodes=parse(sys.argv[1])
    for n in nodes: print(n[0], len(n[2]))
    objs=[n for n in nodes if n[0]=='Objects'][0]
    from collections import Counter
    print(Counter(c[0] for c in objs[2]))
    for c in objs[2]:
        nm=c[1][1] if len(c[1])>1 else ''
        print(c[0], c[1][0], repr(nm)[:60], c[1][2] if len(c[1])>2 else '', [k[0] for k in c[2]][:12])
