package tj.builder.v1;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Changes only confirmed identity attributes on the Builder-owned APK template. */
public final class ManifestIdentity {
    private static final int XML=3, POOL=1, START=0x102, STRING=3, DECIMAL=0x10;
    private static int half(byte[] b,int pos){return (b[pos]&255)|((b[pos+1]&255)<<8);}
    private static int word(byte[] b,int pos){return half(b,pos)|(half(b,pos+2)<<16);}
    private static void put(byte[] b,int pos,int n){for(int i=0;i<4;i++)b[pos+i]=(byte)(n>>>(8*i));}
    private static void write32(ByteArrayOutputStream out,int n){for(int i=0;i<4;i++)out.write(n>>>(8*i));}
    private static void write16(ByteArrayOutputStream out,int n){out.write(n);out.write(n>>>8);}
    private static int size8(byte[] b,int[] at) {
        int n=b[at[0]++]&255;return (n&128)==0?n:((n&127)<<8)|(b[at[0]++]&255);
    }
    private static void size8(ByteArrayOutputStream b,int n){
        if(n>=128)b.write(128|(n>>>8));b.write(n);
    }
    private static List<String> strings(byte[] p,boolean utf8) throws IOException{
        int base=word(p,20),count=word(p,8);List<String> a=new ArrayList<>();
        if(count<1||count>4096||base<28+count*4||base>=p.length)throw new IOException("pool");
        for(int i=0;i<count;i++){
            int[] at={base+word(p,28+i*4)};
            if(utf8){size8(p,at);int n=size8(p,at);a.add(new String(p,at[0],n,StandardCharsets.UTF_8));}
            else{
                int n=half(p,at[0]);at[0]+=2;
                if((n&0x8000)!=0){n=((n&32767)<<16)|half(p,at[0]);at[0]+=2;}
                a.add(new String(p,at[0],n*2,StandardCharsets.UTF_16LE));
            }
        }
        return a;
    }
    private static byte[] appendStrings(byte[] p,boolean utf8,String[] extra)throws IOException{
        if(half(p,2)!=28||word(p,12)!=0||word(p,24)!=0)throw new IOException("styled pool");
        int n=word(p,8),base=word(p,20);
        ByteArrayOutputStream data=new ByteArrayOutputStream();data.write(p,base,p.length-base);
        int[] indexes=new int[extra.length];
        for(int i=0;i<extra.length;i++){
            indexes[i]=data.size();
            byte[] b=extra[i].getBytes(utf8?StandardCharsets.UTF_8:StandardCharsets.UTF_16LE);
            if(utf8){size8(data,extra[i].length());size8(data,b.length);}
            else write16(data,extra[i].length());
            data.write(b);data.write(0);if(!utf8)data.write(0);
        }
        while((data.size()&3)!=0)data.write(0);
        int newBase=28+4*(n+extra.length);
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(p,0,8);
        write32(out,n+extra.length);write32(out,0);write32(out,word(p,16)&~1);
        write32(out,newBase);write32(out,0);
        out.write(p,28,n*4);for(int off:indexes)write32(out,off);data.writeTo(out);
        byte[] result=out.toByteArray();put(result,4,result.length);return result;
    }
    public static byte[] change(byte[] source,String pkg,String title,String version,int code)throws IOException{
        if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:[.][A-Za-z][A-Za-z0-9_]*)+")||pkg.length()>120||
           title.isEmpty()||title.length()>80||version.isEmpty()||version.length()>24||code<1||code>2100000000)
            throw new IOException("invalid identity");
        try {
            if(source.length<36||half(source,0)!=XML||half(source,2)!=8||word(source,4)!=source.length||half(source,8)!=POOL)
                throw new IOException("binary XML");
            int end=8+word(source,12);
            byte[] pool=Arrays.copyOfRange(source,8,end);
            boolean utf8=(word(pool,16)&256)!=0;
            List<String> names=strings(pool,utf8);int extra=names.size();
            int found=0;
            byte[] tail=Arrays.copyOfRange(source,end,source.length);
            for(int pos=0;pos<tail.length;){
                if(tail.length-pos<8)throw new IOException("node");
                int len=word(tail,pos+4);
                if(len<8||len>tail.length-pos)throw new IOException("size");
                if(half(tail,pos)==START){
                    int e=pos+half(tail,pos+2);
                    String element=names.get(word(tail,e+4));
                    int start=e+half(tail,e+8),attributeSize=half(tail,e+10),count=half(tail,e+12);
                    if(attributeSize<20||start+(long)attributeSize*count>pos+len)throw new IOException("attributes");
                    for(int i=0;i<count;i++){
                        int a=start+i*attributeSize;String name=names.get(word(tail,a+4));
                        int index=-1;
                        if(element.equals("manifest")){
                            if(name.equals("package")&&word(tail,a)==-1){index=extra;found|=1;}
                            else if(name.equals("versionName")){index=extra+2;found|=4;}
                            else if(name.equals("versionCode")){
                                put(tail,a+8,-1);tail[a+15]=DECIMAL;put(tail,a+16,code);found|=8;
                            }
                        } else if(element.equals("application")&&name.equals("label")){index=extra+1;found|=2;}
                        if(index>=0){put(tail,a+8,index);tail[a+15]=STRING;put(tail,a+16,index);}
                    }
                }
                pos+=len;
            }
            if(found!=15)throw new IOException("missing identity");
            byte[] replacement=appendStrings(pool,utf8,new String[]{pkg,title,version});
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            out.write(source,0,4);write32(out,8+replacement.length+tail.length);out.write(replacement);out.write(tail);
            return out.toByteArray();
        }catch(IndexOutOfBoundsException|IllegalArgumentException e){throw new IOException("invalid template",e);}
    }
}
