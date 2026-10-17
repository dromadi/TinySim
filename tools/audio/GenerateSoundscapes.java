import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Original seeded synthesis. No samples, downloads or third-party music. Java 17+. */
class GenerateSoundscapes {
    static final int RATE=48000, SECONDS=300;
    static void le(DataOutputStream out,int v,int bytes)throws IOException { for(int i=0;i<bytes;i++)out.write(v>>>(i*8)); }
    public static void main(String[] args)throws Exception {
        Path dir=Path.of(args[0]);Files.createDirectories(dir);
        String[] names={"quarry","workshop","garden"};StringBuilder manifest=new StringBuilder("{\"license\":\"MIT; original procedural synthesis\",\"sampleRate\":48000,\"channels\":2,\"bits\":16,\"tracks\":[");
        for(int zone=0;zone<3;zone++) {
            Path file=dir.resolve(names[zone]+".wav");Random random=new Random(7301+zone);
            int samples=RATE*SECONDS;double left=0,right=0,low=0,phase=0,peak=0,energy=0;
            try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file),1<<20))) {
                out.writeBytes("RIFF");le(out,36+samples*4,4);out.writeBytes("WAVEfmt ");le(out,16,4);le(out,1,2);le(out,2,2);le(out,RATE,4);le(out,RATE*4,4);le(out,4,2);le(out,16,2);out.writeBytes("data");le(out,samples*4,4);
                for(int i=0;i<samples;i++) {
                    double t=i/(double)RATE;
                    left=.975*left+.025*(random.nextDouble()*2-1);right=.975*right+.025*(random.nextDouble()*2-1);
                    low=.9993*low+.0007*(random.nextDouble()*2-1);
                    double gust=.55+.25*Math.sin(t*.071+zone)+.13*Math.sin(t*.193);
                    double bird=0, cycle=t%(zone==2?13.7:21.3);
                    if(cycle>2 && cycle<3.4) {
                        double b=cycle-2,envelope=Math.pow(Math.sin(Math.PI*b/1.4),2);
                        phase+=2*Math.PI*(1550+550*Math.sin(b*16)+zone*180)/RATE;
                        bird=.045*envelope*Math.sin(phase)*(.65+.35*Math.sin(b*50));
                    }
                    double motor=zone==1?.018*Math.sin(2*Math.PI*82*t)*(1+.2*Math.sin(t*.4)):0;
                    double stream=zone==2?.025*(random.nextDouble()*2-1)*(.5+.5*Math.sin(t*.37)):0;
                    double fade=Math.min(1,Math.min(t/4,(SECONDS-t)/4));fade=fade*fade*(3-2*fade);
                    double a=(left*gust*.6+low*1.1+bird+motor+stream)*fade;
                    double b=(right*gust*.6+low*1.1+bird*.7+motor+stream*.65)*fade;
                    peak=Math.max(peak,Math.max(Math.abs(a),Math.abs(b)));energy+=a*a+b*b;
                    le(out,(int)(Math.max(-.35,Math.min(.35,a))*32767),2);le(out,(int)(Math.max(-.35,Math.min(.35,b))*32767),2);
                }
            }
            MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)digest.update(buf,0,n);}
            if(zone>0)manifest.append(',');manifest.append(String.format(Locale.ROOT,"{\"file\":\"%s.wav\",\"seconds\":300,\"bytes\":%d,\"peakBeforeLimiter\":%.6f,\"rms\":%.6f,\"sha256\":\"%s\"}",names[zone],Files.size(file),peak,Math.sqrt(energy/(samples*2.0)),HexFormat.of().formatHex(digest.digest())));
            System.out.println(names[zone]+": 300s stereo PCM, "+Files.size(file)+" bytes");
        }
        Files.writeString(dir.resolve("index.json"),manifest.append("]}\n").toString());
    }
}
