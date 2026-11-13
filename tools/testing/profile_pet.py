"""Profile isolated real SVG pets without credentials, paid actions or app-data reset.

Build :shared:assembleAndroidDeviceTest, then run --label before / after using
the SAME device, graphics backend and test build. Artifacts stay under build/.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

ROOT=Path(__file__).resolve().parents[2]

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--label',required=True,choices=['before','after'])
    p.add_argument('--serial',default='emulator-5554')
    args=p.parse_args()
    adb=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
    out=ROOT/'build/performance'/args.label
    out.mkdir(parents=True,exist_ok=True)
    def run(*cmd,check=True):
        return subprocess.run([str(adb),'-s',args.serial,*cmd],capture_output=True,text=True,check=check).stdout
    run('install','-r',str(ROOT/'shared/build/outputs/apk/androidTest/shared-androidTest.apk'))
    surface=run('shell','dumpsys','SurfaceFlinger')
    spec={name:run('shell','getprop',prop).strip() for name,prop in {
        'android':'ro.build.version.release','abi':'ro.product.cpu.abi','model':'ro.product.model'}.items()}
    spec['display']=[line.strip() for line in surface.splitlines() if 'GLES:' in line or 'vsyncRate=' in line]
    (out/'device.json').write_text(json.dumps(spec,indent=2))
    trace=f'/data/misc/perfetto-traces/pawquest-{args.label}.pftrace'
    trace_start=run('shell','perfetto','--background','-o',trace,'-t','70s','-b','16mb','sched','freq','idle','am','wm','gfx','view',check=False)
    (out/'trace-start.txt').write_text(trace_start)
    result=run('shell','am','instrument','-w','-e','perfLabel',args.label,'-e','class',
        'com.pawquest.PetPerformanceDeviceTest#rendererProfileAndLifecycle','com.pawquest.shared.test/androidx.test.runner.AndroidJUnitRunner')
    (out/'instrumentation.txt').write_text(result)
    print(result)
    if 'OK (1 test)' not in result:
        raise SystemExit('Profiling test failed; do not report metrics as successful.')
    run('pull',f'/sdcard/Android/data/com.pawquest.shared.test/files/pet-perf-{args.label}.json',str(out/'pet.json'))
    (out/'gfxinfo.txt').write_text(run('shell','dumpsys','gfxinfo','com.pawquest.shared.test'))
    # Fixture takes about 67 seconds; let the bounded 70-second trace flush.
    time.sleep(5)
    run('pull',trace,str(out/'system.pftrace'),check=False)
    print((out/'pet.json').read_text())

if __name__=='__main__':
    main()
