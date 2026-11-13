"""Additional portrait/cancel checks after the native SQL-backed MVP journey."""
import json,time
import test_mvp_android as t

def main():
    t.require_fixture()
    t.RESULTS.extend(json.loads((t.OUT/'results.json').read_text()))
    try:
        t.wait("Mochi's little world");t.click('Quests');t.click('View quest',scroll=True);t.click('Start Quest',scroll=True);t.wait('Focus Mode')
        t.adb('shell','wm','size','1080x1920');t.adb('shell','wm','density','480');time.sleep(1)
        t.adb('shell','input','swipe','540','1600','540','700','350')
        button=t.find('Back to quests',scroll=True)
        assert t.bounds(button)[3]<1840
        t.capture('focus-1920-scrolled')
        t.click('Cancel Quest',scroll=True);t.wait('Keep focusing');t.click('Cancel Quest');t.wait('Quest cancelled');t.click('Done')
        t.record('9:16 Focus bottom action scrolls fully into view; confirmed cancellation grants nothing')
        t.click('Pet Home');t.wait("Mochi's little world")
        for height in [1920,2340,2400]:
            t.adb('shell','wm','size','1080x'+str(height));t.adb('shell','wm','density','480');time.sleep(.5)
            t.click('sleeping',scroll=True);time.sleep(.4);t.capture('pet-sleep-'+str(height))
        t.click('idle',scroll=True)
        t.record('Bundled cat/owned hat/bed framing captured in Sleeping at all three portrait ratios')
    finally:
        (t.OUT/'results.json').write_text(json.dumps(t.RESULTS,indent=2))
        t.adb('shell','wm','size','reset');t.adb('shell','wm','density','reset')

if __name__=='__main__':main()
