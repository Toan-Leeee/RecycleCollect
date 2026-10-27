"""Exercise the native prototype on a connected emulator and capture its screens."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(r'C:\Users\acer\AppData\Local\Android\Sdk\platform-tools\adb.exe')
OUT = ROOT / 'screenshots'
OUT.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.run([str(ADB), '-s', 'emulator-5554', *args], check=True, capture_output=True).stdout.decode('utf-8', errors='replace')

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/recycle-window.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/recycle-window.xml'))

def find(label, attr='text', exact=True):
    nodes = list(tree().iter('node'))
    nodes.sort(key=lambda n: n.attrib.get('clickable') != 'true')
    for node in nodes:
        value = node.attrib.get(attr, '')
        if (value.casefold() == label.casefold() if exact else label.casefold() in value.casefold()):
            return node
    raise AssertionError(f'UI element missing: {label}; visible text: {[n.attrib.get("text") for n in nodes if n.attrib.get("text")]}')

def tap(label, attr='text', exact=True):
    node = find(label, attr, exact)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.4)

def shot(name):
    adb('shell', 'screencap', '-p', '/sdcard/recycle-shot.png')
    adb('pull', '/sdcard/recycle-shot.png', str(OUT / f'{name}.png'))
    print('Captured', name, flush=True)

def enter(label, value):
    tap(label)
    adb('shell', 'input', 'text', value)

def back():
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(.3)

def role(name):
    tap('Tùy chọn bản giao diện', 'content-desc')
    tap('Xem thử: ' + name)

def scroll_down():
    adb('shell', 'input', 'swipe', '210', '700', '210', '300', '300')
    time.sleep(.25)

def run():
    adb('shell', 'am', 'force-stop', 'vn.edu.recyclecollect')
    adb('shell', 'am', 'start', '-n', 'vn.edu.recyclecollect/.MainActivity')
    time.sleep(2)
    find('Chưa có tài khoản? Đăng ký ngay')
    shot('01-login')
    tap('Chưa có tài khoản? Đăng ký ngay'); shot('02-register'); back()
    scroll_down(); tap('Người dân'); tap('Đăng nhập'); find('Nguyễn Minh Anh'); shot('03-home')
    tap('Hồ sơ'); shot('04-profile'); tap('Trang chủ')
    tap('Danh mục và bảng giá'); shot('05-prices'); back()
    tap('＋  Tạo yêu cầu thu gom'); shot('06-create')
    # New forms and incomplete fields must not create a request.
    scroll_down(); tap('Gửi yêu cầu mô phỏng'); back(); back()
    tap('Yêu cầu'); shot('07-requests')
    tap('TG-001 Giấy / carton, Chờ nhận','content-desc'); shot('08-detail')
    role('Người thu gom')
    tap('Tìm yêu cầu chờ nhận'); shot('09-explore')
    tap('Xem bản đồ minh họa'); shot('10-map'); back()
    scroll_down(); tap('TG-001 Giấy / carton, Chờ nhận','content-desc')
    scroll_down(); tap('Nhận đơn thu gom'); tap('Xác nhận'); scroll_down(); tap('Nhắn tin'); shot('11-chat'); back()
    scroll_down(); tap('Nhập kết quả thu gom'); shot('12-result')
    enter('Khối lượng thực tế (kg)', '4.5'); back(); scroll_down(); tap('Gửi kết quả thu gom')
    role('Người dân'); tap('Yêu cầu'); tap('Đã nhận')
    tap('TG-001 Giấy / carton, Đã nhận','content-desc'); scroll_down(); tap('Xem kết quả thu gom')
    find('13.500 đ'); shot('12b-confirm'); tap('Xác nhận hoàn tất'); tap('Xác nhận')
    tap('Lịch sử'); shot('13-history')
    role('Admin'); tap('Quản lý danh mục và giá'); shot('14-admin-materials')
    tap('＋  Thêm vật liệu'); shot('15-admin-edit')
    role('Người dân')
    print('UI smoke passed: 15 screen templates and collect/confirm flow.', flush=True)

if __name__ == '__main__':
    run()
