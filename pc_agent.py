import os
import time 
import logging
import threading
from datetime import datetime, timedelta
from io import BytesIO
import firebase_admin 
from firebase_admin import credentials, db, storage
import requests   # internet kontrolu icin 
import psutil
from PIL import Image
import pyautogui
import screen_brightness_control as sbc
import cv2
# Windows specific imports
import win32api
import win32con
import win32gui # başlık çekme
import win32process # pencereye ait PID alam
import win32ui
import pythoncom 
from ctypes import POINTER, cast
from comtypes import CLSCTX_ALL
from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume # ses kontrolu 

# Basic Configuration Setup

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Logging settings
logging.basicConfig(
    filename=os.path.join(BASE_DIR, 'pc_agent.log'),
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)

# Global variables
camera = None
camera_open = False
shutdown_in_progress = False
start_time = time.time()
pause_start_time = None
paused_duration = 0
audio_controller = None
bucket = None
ref_commands = None
ref_status = None

# List of titles to ignore

IGNORED_TITLES = [
    "Program Manager",
    "Settings",
    "Windows Input Experience",
    "",
    "Microsoft Text Input Application",
    "Task Switching",
    "Start",
    "Task View",
    "Desktop Window Manager",
    "Cortana",
    "Action Center",
    "Notification Center"
]


def should_ignore(title):
    if title in IGNORED_TITLES:
        return True
    if len(title.strip()) < 3:
        return True
    return False

# Extract clean application name

def extract_clean_name(window_title, exe_name):
    title = window_title.strip()

    # If title contains "-" → take last part
    if "-" in title:
        parts = title.split("-")
        clean = parts[-1].strip()
        if clean:
            return clean

    # If title is short and logical → use it
    if 3 <= len(title) <= 40:
        return title

    # fallback → exe name
    return exe_name.replace(".exe", "").strip().capitalize()


# Extract icon from exe file

def extract_icon_from_exe(exe_path, output_png):
    try:
        large, small = win32gui.ExtractIconEx(exe_path, 0)
        if large:
            hicon = large[0]
        elif small:
            hicon = small[0]
        else:
            return None

        hdc = win32ui.CreateDCFromHandle(win32gui.GetDC(0))
        hbmp = win32ui.CreateBitmap()
        hbmp.CreateCompatibleBitmap(hdc, 256, 256)
        hdc2 = hdc.CreateCompatibleDC()

        hdc2.SelectObject(hbmp)
        win32gui.DrawIconEx(hdc2.GetSafeHdc(), 0, 0, hicon, 256, 256, 0, None, win32con.DI_NORMAL)

        bmpinfo = hbmp.GetInfo()
        bmpstr = hbmp.GetBitmapBits(True)

        img = Image.frombuffer("RGBA",
                               (bmpinfo['bmWidth'], bmpinfo['bmHeight']),
                               bmpstr,
                               "raw",
                               "BGRA",
                               0, 1)

        img.save(output_png)
        return output_png
    
    except Exception as e:
        logging.error(f"ICON EXTRACT ERROR: {e}")
        return None


# Upload icon to Firebase

def upload_icon_if_needed(app_name, exe_path):
    try:
        safe_name = "".join(c for c in app_name if c.isalnum() or c in "_- ").replace(" ", "_")

        ref_icons = db.reference("app_icons").child(safe_name)

        existing = ref_icons.get()
        if existing and "icon_url" in existing:
            return existing["icon_url"]

        # extract
        icon_local = f"{safe_name}.png"
        result = extract_icon_from_exe(exe_path, icon_local)
        if result is None:
            return None

        # upload
        blob = bucket.blob(f"app_icons/{safe_name}.png")
        blob.upload_from_filename(icon_local, content_type="image/png")

        # DO NOT CHANGE — same as your code
        url = blob.generate_signed_url(
            version="v4",
            expiration=timedelta(days=7),
            method="GET"
        )

        # save to DB
        ref_icons.set({
            "icon_url": url
        })

        os.remove(icon_local)

        return url
    
    except Exception as e:
        logging.error(f"UPLOAD ICON ERROR: {e}")
        return None


# Get visible windows

def update_running_apps():
    ref_running = db.reference("running_apps")

    while True:
        try:
            apps_dict = {}

            def callback(hwnd, _):
                title = win32gui.GetWindowText(hwnd).strip()

                if not win32gui.IsWindowVisible(hwnd):
                    return
                if should_ignore(title):
                    return

                _, pid = win32process.GetWindowThreadProcessId(hwnd)

                try:
                    proc = psutil.Process(pid)
                    exe = proc.exe()
                    exe_name = os.path.basename(exe)

                    clean_name = extract_clean_name(title, exe_name)
                    safe_name = clean_name.replace(" ", "_")

                    # Extract icon (same as your code)
                    icon_url = upload_icon_if_needed(clean_name, exe)

                    # Memory usage
                    mem_bytes = proc.memory_info().rss
                    mem_mb = mem_bytes / (1024 * 1024)
                    mem_str = f"{mem_mb:.1f} MB"

                    # CPU usage
                    cpu_usage = proc.cpu_percent(interval=0.0)
                    cpu_str = f"{cpu_usage:.1f}%"

                    apps_dict[safe_name] = {
                        "pid": pid,
                        "name": clean_name,
                        "title": title,
                        "icon": icon_url,
                        "memory": mem_str,
                        "cpu": cpu_str
                    }

                except Exception as e:
                    logging.error(f"Process scan error: {e}")
                    pass

            win32gui.EnumWindows(callback, None)

            ref_running.set(apps_dict)

            time.sleep(3)

        except Exception as e:
            logging.error(f"update_running_apps error: {e}")
            time.sleep(5)


# Helper functions

def check_internet():
    """Check internet connection"""
    try:
        response = requests.get("https://www.google.com", timeout=2)
        return response.status_code == 200
    except:
        return False

def wait_for_internet():
    """Wait for internet connection"""
    while not check_internet():
        logging.warning("Waiting for internet connection...")
        time.sleep(2)
    logging.info("Connected to internet")

def format_uptime(seconds):
    """Format uptime"""
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    seconds = int(seconds % 60)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d}"

# =========================================
# Audio control classes
# =========================================

class AudioController:
    """Control audio volume"""
    
    def __init__(self):
        self.volume_interface = None
        self.init_audio()
    
    def init_audio(self):
        """Initialize audio system"""
        try:
            pythoncom.CoInitialize()
            devices = AudioUtilities.GetSpeakers()
            interface = devices.Activate(
                IAudioEndpointVolume._iid_, 
                CLSCTX_ALL, 
                None
            )
            self.volume_interface = cast(interface, POINTER(IAudioEndpointVolume))
            logging.info("Audio system initialized successfully")
            return True
        except Exception as e:
            logging.error(f"Audio initialization error: {e}")
            return False
    
    def volume_up(self):
        """Increase volume"""
        try:
            if self.volume_interface:
                current_vol = self.volume_interface.GetMasterVolumeLevelScalar()
                new_vol = min(1.0, current_vol + 0.1)
                self.volume_interface.SetMasterVolumeLevelScalar(new_vol, None)
                logging.info(f"Increased volume from {current_vol:.1f} to {new_vol:.1f}")
                return True
            return False
        except Exception as e:
            logging.error(f"Volume increase error: {e}")
            return False
    
    def volume_down(self):
        """Decrease volume"""
        try:
            if self.volume_interface:
                current_vol = self.volume_interface.GetMasterVolumeLevelScalar()
                new_vol = max(0.0, current_vol - 0.1)
                self.volume_interface.SetMasterVolumeLevelScalar(new_vol, None)
                logging.info(f"Decreased volume from {current_vol:.1f} to {new_vol:.1f}")
                return True
            return False
        except Exception as e:
            logging.error(f"Volume decrease error: {e}")
            return False
    
    def mute_toggle(self):
        """Toggle mute"""
        try:
            if self.volume_interface:
                is_muted = self.volume_interface.GetMute()
                self.volume_interface.SetMute(not is_muted, None)
                logging.info(f"Toggled mute to: {not is_muted}")
                return True
            return False
        except Exception as e:
            logging.error(f"Mute toggle error: {e}")
            return False

def initialize_audio():
    """Initialize audio control"""
    global audio_controller
    audio_controller = AudioController()

# General control functions

def volume_up():
    """Increase volume with fallback"""
    if audio_controller and audio_controller.volume_up():
        return True
    # Fallback using win32api
    try:
        win32api.keybd_event(win32con.VK_VOLUME_UP, 0)
        win32api.keybd_event(win32con.VK_VOLUME_UP, 0, win32con.KEYEVENTF_KEYUP)
        logging.info("Volume up (win32api)")
        return True
    except Exception as e:
        logging.error(f"Volume up fallback error: {e}")
        return False

def volume_down():
    """Decrease volume with fallback"""
    if audio_controller and audio_controller.volume_down():
        return True
    # Fallback using win32api
    try:
        win32api.keybd_event(win32con.VK_VOLUME_DOWN, 0)
        win32api.keybd_event(win32con.VK_VOLUME_DOWN, 0, win32con.KEYEVENTF_KEYUP)
        logging.info("Volume down (win32api)")
        return True
    except Exception as e:
        logging.error(f"Volume down fallback error: {e}")
        return False

def mute_toggle():
    """Toggle mute with fallback"""
    if audio_controller and audio_controller.mute_toggle():
        return True
    # Fallback using win32api
    try:
        win32api.keybd_event(win32con.VK_VOLUME_MUTE, 0)
        win32api.keybd_event(win32con.VK_VOLUME_MUTE, 0, win32con.KEYEVENTF_KEYUP)
        logging.info("Mute toggle (win32api)")
        return True
    except Exception as e:
        logging.error(f"Mute toggle fallback error: {e}")
        return False

def brightness_up():
    """Increase screen brightness"""
    try:
        current = sbc.get_brightness(display=0)[0]
        new_brightness = min(current + 10, 100)
        sbc.set_brightness(new_brightness)
        logging.info(f"Increased brightness from {current}% to {new_brightness}%")
    except Exception as e:
        logging.error(f"Brightness increase error: {e}")

def brightness_down():
    """Decrease screen brightness"""
    try:
        current = sbc.get_brightness(display=0)[0]
        new_brightness = max(current - 10, 0)
        sbc.set_brightness(new_brightness)
        logging.info(f"Decreased brightness from {current}% to {new_brightness}%")
    except Exception as e:
        logging.error(f"Brightness decrease error: {e}")

# Screen and camera functions

def take_screenshot():
    """Take screenshot"""
    try:
        logging.info("Starting screenshot capture")
        
        # Capture screenshot
        screenshot = pyautogui.screenshot()
        img_byte_arr = BytesIO()
        screenshot.save(img_byte_arr, format='PNG')
        img_byte_arr.seek(0)
        
        # Create filename
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        filename = f"screenshot_{timestamp}.png"
        
        # Upload to Firebase Storage
        blob = bucket.blob(f"screenshots/{filename}")
        blob.upload_from_file(img_byte_arr, content_type='image/png')
        logging.info("Uploaded image to Firebase Storage")
        
        # Generate temporary URL
        url = blob.generate_signed_url(
            version="v4",
            expiration=timedelta(days=7),
            method="GET"
        )
        
        # Save info to database
        ref = db.reference("images/screenshots")
        ref.push({
            "filename": filename,
            "url": url,
            "timestamp": int(time.time())
        })
        
        logging.info("Saved image to database")
        return True
        
    except Exception as e:
        logging.error(f"Screenshot capture error: {e}")
        return False

def open_camera():
    """Open camera"""
    global camera, camera_open
    try:
        camera = cv2.VideoCapture(0)
        if camera.isOpened():
            camera_open = True
            logging.info("Camera opened successfully")
            return True
        else:
            camera_open = False
            logging.error("Failed to open camera")
            return False
    except Exception as e:
        logging.error(f"Camera open error: {e}")
        return False

def capture_camera_image():
    """Capture image from camera"""
    global camera, camera_open
    
    if not camera_open or camera is None:
        logging.warning("Camera is not open")
        return False
    
    try:
        # Capture image
        ret, frame = camera.read()
        if not ret:
            logging.error("Failed to capture image from camera")
            return False
        
        # Convert image
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        img = Image.fromarray(frame_rgb)
        
        # Save image to memory
        img_byte_arr = BytesIO()
        img.save(img_byte_arr, format='JPEG')
        img_byte_arr.seek(0)
        
        # Create filename
        filename = f"camera_{int(time.time())}.jpg"
        
        # Upload to Firebase
        blob = bucket.blob(f"camera/{filename}")
        blob.upload_from_file(img_byte_arr, content_type='image/jpeg')
        logging.info("Uploaded camera image")
        
        # Generate URL
        url = blob.generate_signed_url(
            version="v4",
            expiration=timedelta(days=7),
            method="GET"
        )
        
        # Save to database
        ref = db.reference("images/camera")
        ref.push({
            "filename": filename,
            "url": url,
            "timestamp": int(time.time())
        })
        
        logging.info("Saved camera image to database")
        return True
        
    except Exception as e:
        logging.error(f"Camera capture error: {e}")
        return False

def close_camera():
    """Close camera"""
    global camera, camera_open
    try:
        if camera_open and camera is not None:
            camera.release()
            camera_open = False
            logging.info("Camera closed")
            return True
        return False
    except Exception as e:
        logging.error(f"Camera close error: {e}")
        return False

# Update system status

def update_system_status():
    global start_time, pause_start_time, paused_duration, shutdown_in_progress

    while True:
        if shutdown_in_progress:
            time.sleep(3)
            continue

        try:
            current_time = time.time()

            # uptime
            uptime_seconds = current_time - start_time - paused_duration

            # update
            ref_status.update({
                "uptime": format_uptime(uptime_seconds),
                "last_seen": int(current_time)
            })

            time.sleep(1)

        except Exception as e:
            logging.error(f"update_system_status error: {e}")
            time.sleep(5)


# Command processing

def handle_command(command):
    """Handle incoming commands"""
    global shutdown_in_progress, pause_start_time, paused_duration, start_time
    
    if not command or command == "None":
        return
    
    logging.info(f"Received command: {command}")
    
    try:
        if command == "sleep":
           pause_start_time = time.time()
           ref_commands.set("None")
           os.system("rundll32.exe powrprof.dll,SetSuspendState 0,1,0")

        elif command == "logout":
            pause_start_time = time.time()
            ref_commands.set("None")
            os.system("shutdown /l")
            
        elif command == "shutdown":
             shutdown_in_progress = True
             now_ts = int(time.time())
             ref_status.update({
                "uptime": "00:00:00",
                "last_seen": now_ts
           })
             ref_commands.set("None")
             os.system("shutdown /s /t 0")
            
        elif command == "restart":
             shutdown_in_progress = True
             now_ts = int(time.time())
             ref_status.update({
                  "uptime": "00:00:00",
                  "last_seen": now_ts,
                  "online": False
            })
             ref_commands.set("None")
             os.system("shutdown /r /t 0")
            
        elif command == "volume_up":
            volume_up()
            ref_commands.set("None")
            
        elif command == "volume_down":
            volume_down()
            ref_commands.set("None")
            
        elif command == "mute":
            mute_toggle()
            ref_commands.set("None")
            
        elif command == "brightness_up":
            brightness_up()
            ref_commands.set("None")
            
        elif command == "brightness_down":
            brightness_down()
            ref_commands.set("None")
            
        elif command == "screenshot":
            take_screenshot()
            ref_commands.set("None")
            
        elif command == "camera":
            open_camera()
            ref_commands.set("None")
            
        elif command == "capture_image":
            capture_camera_image()
            ref_commands.set("None")
            
        elif command == "close_camera":
            close_camera()
            ref_commands.set("None")
        elif command.startswith("kill:"):
           try:
              pid = int(command.split(":")[1])
              os.system(f"taskkill /PID {pid} /F")
              logging.info(f"Killed PID: {pid}")
           except Exception as e:
              logging.error(f"Kill command error: {e}")
           finally:
                ref_commands.set("None")
        else:
            logging.warning(f"Unknown command: {command}")
            ref_commands.set("None")
            
    except Exception as e:
        logging.error(f"Error processing command {command}: {e}")
        ref_commands.set("None")

def on_command_change(event):
    """Callback function for command changes"""
    command = event.data if isinstance(event.data, str) else None
    handle_command(command)

# Main initialization

def initialize_firebase():
    """Initialize Firebase connection"""
    global bucket, ref_commands, ref_status
    
    # Wait for internet connection
    wait_for_internet()
    
    # Load service file
    sa_filename = "bilgisayar-kontrolu-firebase-adminsdk-fbsvc-655a679099.json"
    sa_path = os.path.join(BASE_DIR, sa_filename)
    
    if not os.path.exists(sa_path):
        raise FileNotFoundError(f"Firebase service file not found: {sa_path}")
    
    # Initialize Firebase
    cred = credentials.Certificate(sa_path)
    firebase_admin.initialize_app(cred, {
        'databaseURL': 'https://bilgisayar-kontrolu-default-rtdb.firebaseio.com/',
        'storageBucket': 'bilgisayar-kontrolu.firebasestorage.app'
    })
    
    # Setup references
    bucket = storage.bucket()
    ref_commands = db.reference("commands")
    ref_status = db.reference("status")
    
    # Update device information
    computer_name = os.getenv("COMPUTERNAME", "Unknown")
    now_ts = int(time.time())
    ref_status.update({
        "computer_name": computer_name,
        "uptime": "00:00:00",
        "last_seen": now_ts,    # Last time agent was seen
    })
    
    logging.info(f"Connected to Firebase - Device: {computer_name}")
    print(f"✅ Connected to Firebase - Device: {computer_name}")

# Start background services

def start_background_services():
    """Start background services"""
    # Update system status
    status_thread = threading.Thread(target=update_system_status, daemon=True)
    status_thread.start()
    
    # Update running applications
    apps_thread = threading.Thread(target=update_running_apps, daemon=True)
    apps_thread.start()
    
    logging.info("Background services started")
    print("🔄 Background services running...")

# Main function

def main():
    """Main function"""
    try:
        # Log startup
        logging.info("Starting agent...")
        print("🖥️ Starting PC control agent...")
        
        # Initialize systems
        initialize_firebase()
        initialize_audio()
        
        # Start background services
        start_background_services()
        
        # Listen for commands
        print("👂 Ready to receive commands...")
        print("📱 Running apps tracking active...")
        logging.info("Agent running and ready for commands")
        
        if audio_controller and audio_controller.volume_interface:
            print("🔊 Audio control system active")
        else:
            print("⚠️ Audio control system limited")
        
        # Listen for database changes
        ref_commands.listen(on_command_change)
        
    except KeyboardInterrupt:
        logging.info("Agent stopped by user")
        print("\n🛑 Stopping agent...")
    except Exception as e:
        logging.error(f"Unexpected error: {e}")
        print(f"❌ Error: {e}")


if __name__ == "__main__":
    main()