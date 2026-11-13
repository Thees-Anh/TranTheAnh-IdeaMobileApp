"""Run isolated Wheel/Shop UI fixtures at required phone ratios; always restore wm size.

Requires the already-built/installed com.pawquest.shared.test APK and a connected emulator.
Does not create a Supabase client or touch the user's account or economy.
"""
import os
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[2]
ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'


def adb(*args):
    result=subprocess.run([str(ADB),*args],capture_output=True,text=True,encoding='utf-8',errors='replace',check=True)
    return result.stdout


def main():
    original=adb('shell','wm','size')
    override=re.search(r'Override size:\s*(\d+x\d+)',original)
    restored=override.group(1) if override else 'reset'
    reports=ROOT/'build/reports/lucky-wheel-portraits'
    reports.mkdir(parents=True,exist_ok=True)
    try:
        for width,height,label in [(1080,1920,'9x16'),(1080,2340,'19.5x9'),(1080,2400,'20x9')]:
            adb('shell','wm','size',f'{width}x{height}')
            tests=','.join('com.pawquest.LuckyWheelShopDeviceTest#'+method for method in [
                'fourShopSectionsFoodFlowAndNoDirectNonFoodPurchases','coinAndTicketSpinsRevealConfirmedPetAndNeverAutoOpenAgain'])
            result=adb('shell','am','instrument','-w','-r','-e','class',tests,
                       'com.pawquest.shared.test/androidx.test.runner.AndroidJUnitRunner')
            (reports/f'{label}.txt').write_text(result,encoding='utf-8')
            if 'OK (2 tests)' not in result or 'FAILURES!!!' in result:
                raise AssertionError(f'{label} failed; see {reports / (label+".txt")}')
            print(f'PASS {label}: Shop sections, Food flow, Coins/Ticket, visible wheel pixels and explicit next spin',flush=True)
    finally:
        adb('shell','wm','size',restored)
        print('Restored emulator display size: '+restored,flush=True)


if __name__=='__main__':
    main()
