"""Original short synthesized level-up chime; no external audio asset."""
from pathlib import Path
import io,math,struct,sys,wave
ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
rate=22050;duration=1.0
samples=[]
notes=[(0,523.25),(.16,659.25),(.32,783.99),(.48,1046.5)]
for i in range(int(rate*duration)):
    t=i/rate;value=0.0
    for start,hz in notes:
        age=t-start
        if 0<=age<.48:
            envelope=min(1,age/.012)*max(0,1-age/.48)**2
            value+=.14*envelope*(math.sin(2*math.pi*hz*age)+.25*math.sin(4*math.pi*hz*age))
    samples.append(struct.pack('<h',round(max(-1,min(1,value))*32767)))
buffer=io.BytesIO()
with wave.open(buffer,'wb') as out:
    out.setnchannels(1);out.setsampwidth(2);out.setframerate(rate);out.writeframes(b''.join(samples))
path=ROOT/'androidApp/src/main/res/raw/level_up.wav'
if '--check' in sys.argv:assert path.read_bytes()==buffer.getvalue(),'Stale level-up sound'
else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(buffer.getvalue())
print('PASS level-up sound generation')
