"""Verify the generated offline WAV pack and (optionally) its APK packaging; standard library only."""
import hashlib, json, sys, wave, zipfile
from array import array
from pathlib import Path
root=Path(sys.argv[1]); index=json.loads((root/'index.json').read_text()); result=[]
assert len(index['tracks'])==3
for track in index['tracks']:
    file=root/track['file']
    with wave.open(str(file),'rb') as wav:
        assert (wav.getnchannels(),wav.getsampwidth(),wav.getframerate(),wav.getnframes())==(2,2,48000,14400000)
        first=wav.readframes(48000);wav.setpos(7*48000);later=wav.readframes(48000)
        assert first!=later and any(later)
        wav.setpos(14400000-100);tail=array("h",wav.readframes(100))
        if sys.byteorder!="little":tail.byteswap()
        assert max(abs(sample) for sample in tail)<=4
    digest=hashlib.file_digest(file.open('rb'),'sha256').hexdigest();assert digest==track['sha256']
    assert 0<track['rms']<.05 and track['peakBeforeLimiter']<.35
    result.append(dict(file=file.name,seconds=300,stereo=True,sha256=digest,bytes=file.stat().st_size,status='PASS'))
if len(sys.argv)>2:
    apk=Path(sys.argv[2]);assert apk.stat().st_size>100*1024*1024
    with zipfile.ZipFile(apk) as package:
        expected={'assets/soundscapes/'+t['file'] for t in index['tracks']} | {'assets/soundscapes/index.json'}
        actual={n for n in package.namelist() if n.startswith('assets/soundscapes/')}
        assert actual==expected, f'Unexpected/missing audio assets: {actual ^ expected}'
        assert package.testzip() is None
        for track in index['tracks']:
            entry=package.getinfo('assets/soundscapes/'+track['file']);assert entry.compress_type==zipfile.ZIP_STORED
            assert entry.file_size==track['bytes']
    print(json.dumps(dict(apkBytes=apk.stat().st_size,apkMiB=apk.stat().st_size/1024**2,tracks=result),indent=2))
else:print(json.dumps(result,indent=2))
