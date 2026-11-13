"""Bilingual UI smoke against the disposable SQL fixture, never hosted Supabase."""
import json, sys, time, uuid, zipfile
from pathlib import Path
import test_mvp_android as ui

OUT = ui.ROOT / 'build/language-ui'
OUT.mkdir(exist_ok=True)
ui.OUT = OUT
RESULTS = json.loads((OUT / 'results.json').read_text()) if '--resume-vi' in sys.argv and (OUT / 'results.json').exists() else []

def record(message):
    RESULTS.append(message)
    (OUT / 'results.json').write_text(json.dumps(RESULTS, indent=2), encoding='utf-8')
    print('PASS', message, flush=True)

def field(index, value):
    fields = [n for n in ui.nodes() if n.get('class') == 'android.widget.EditText']
    ui.tap(fields[index])
    ui.adb('shell', 'input', 'keycombination', '113', '29')
    ui.adb('shell', 'input', 'keyevent', '67')
    ui.adb('shell', 'input', 'text', value)

def settle(label):
    time.sleep(1)
    ui.wait(label)

def main():
    if '--resume-vi' in sys.argv:
        package=ui.adb('shell','pm','path','com.pawquest').splitlines()[0].removeprefix('package:')
        ui.adb('pull',package,str(OUT/'fixture.apk'))
        with zipfile.ZipFile(OUT/'fixture.apk') as archive:
            assert any(b'http://127.0.0.1:55447' in archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
        protected_vietnamese()
        return
    apk = ui.ROOT / 'androidApp/build/outputs/apk/debug/androidApp-debug.apk'
    with zipfile.ZipFile(apk) as archive:
        assert any(b'http://127.0.0.1:55447' in archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
    ui.adb('shell', 'am', 'force-stop', 'com.pawquest')
    ui.adb('install', '-r', str(apk))
    ui.adb('reverse', 'tcp:55447', 'tcp:55447')
    ui.adb('shell', 'am', 'start', '-n', 'com.pawquest/.MainActivity')
    time.sleep(3)
    current = ui.texts()
    if 'Sign out' in current: ui.click('Sign out')
    if 'Đăng xuất' in current: ui.click('Đăng xuất')
    ui.wait('English', seconds=30) if 'English' in ui.texts() else None
    if 'Tiếng Việt' in ui.texts(): ui.click('Tiếng Việt')
    settle('Chào mừng trở lại')
    assert 'Sign in' not in ui.texts()
    ui.capture('login-vi')
    ui.click('English')
    settle('Welcome back')
    ui.click('New here? Create an account')
    field(0, 'language-' + uuid.uuid4().hex[:8] + '@example.invalid')
    field(1, 'FixturePass123!')
    field(2, 'FixturePass123!')
    ui.adb('shell', 'input', 'keyevent', '4')
    ui.click('Create account', scroll=True)
    settle("Mochi's little world")
    record('Signed-out language switching and fixture registration work')
    ui.click('Profile')
    settle('Profile & Progress')
    ui.click('Tiếng Việt', scroll=True)
    settle('Nhà thú cưng')
    protected_vietnamese()

def protected_vietnamese():
    ui.click('Nhà thú cưng')
    settle('Thế giới nhỏ của Mochi')
    assert all(x in ui.texts() for x in ['Độ no', 'Nước', 'Năng lượng', 'Cho ăn', 'Uống nước', 'Chơi', 'Ngủ'])
    ui.capture('home-vi')
    ui.click('Nhiệm vụ'); settle('Nhiệm vụ của bạn')
    ui.click('Tạo nhiệm vụ'); settle('Tên nhiệm vụ')
    ui.capture('quest-form-vi')
    ui.adb('shell', 'input', 'keyevent', '4')
    ui.click('Cửa hàng'); settle('Thức ăn')
    ui.click('Phụ kiện'); ui.find('Mũ', scroll=True)
    assert 'Hat' not in ui.texts()
    ui.capture('shop-vi')
    ui.click('Kho đồ'); settle('Vật phẩm')
    ui.click('Thú cưng'); settle('Thú cưng của bạn')
    assert 'Cún con' in ui.texts() or 'Thỏ' in ui.texts()
    ui.capture('pets-vi')
    record('Vietnamese navigation, care controls, quest form, catalog and pet collection render')
    ui.adb('shell', 'am', 'force-stop', 'com.pawquest')
    ui.adb('shell', 'am', 'start', '-n', 'com.pawquest/.MainActivity')
    settle('Thế giới nhỏ của Mochi')
    record('Vietnamese persists after process restart')
    ui.click('Hồ sơ'); settle('Hồ sơ & tiến trình')
    ui.click('English', scroll=True)
    settle('Pet Home')
    ui.click('Shop'); settle('Food')
    ui.click('Accessories'); ui.find('Hat', scroll=True)
    ui.capture('shop-en')
    assert 'Mũ' not in ui.texts()
    ui.click('Pet Home'); settle("Mochi's little world")
    assert all(x in ui.texts() for x in ['Hunger', 'Water', 'Energy', 'Feed', 'Drink', 'Play', 'Sleep'])
    ui.capture('home-en')
    record('English restores all navigation and care/catalog copy')

if __name__ == '__main__': main()
