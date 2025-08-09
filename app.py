import os
import cv2
import numpy as np
import torch
from ultralytics import YOLO
from flask import Flask, render_template, request, jsonify, send_from_directory
from collections import defaultdict
import time
import threading

app = Flask(__name__)

# تنظیمات پیشرفته
UPLOAD_FOLDER = 'uploads'
PROCESSED_FOLDER = 'processed'
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(PROCESSED_FOLDER, exist_ok=True)

# بارگذاری مدل (استفاده از GPU اگر موجود باشد)
device = 'cuda' if torch.cuda.is_available() else 'cpu'
model = YOLO('yolov8n.pt').to(device)

class UltraHeavyTracker:
    def __init__(self):
        self.track_history = defaultdict(lambda: {'positions': [], 'time': 0})
        self.symbols = ['➕', '➖', '✖️']
        self.colors = [(0, 255, 0), (0, 0, 255), (255, 0, 0)]
        self.last_update = time.time()

    def process_frame(self, frame):
        current_time = time.time()
        
        # افزایش کیفیت ورودی
        enhanced_frame = self.enhance_image(frame)
        
        # ردیابی با YOLOv8 + ByteTrack
        results = model.track(
            enhanced_frame,
            persist=True,
            tracker="bytetrack.yaml",
            imgsz=640,
            conf=0.7,
            device=device,
            verbose=False
        )
        
        if results[0].boxes.id is None:
            return frame

        boxes = results[0].boxes.xywh.cpu()
        track_ids = results[0].boxes.id.int().cpu().tolist()
        
        # فقط ۳ شیء اول را نگه دار
        active_tracks = []
        for box, track_id in zip(boxes, track_ids):
            if track_id <= 3:
                active_tracks.append((box, track_id))
        
        # مرتب سازی بر اساس موقعیت X
        active_tracks.sort(key=lambda x: x[0][0])
        
        for box, track_id in active_tracks:
            x, y, w, h = box
            center = (int(x), int(y))
            
            # به روزرسانی تاریخچه ردیابی
            self.update_track_history(track_id, center, current_time)
            
            # رسم علامت با ثبات فوق‌العاده
            self.draw_ultra_stable_symbol(enhanced_frame, track_id)
        
        return enhanced_frame

    def enhance_image(self, frame):
        # افزایش کیفیت تصویر
        lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8,8))
        l = clahe.apply(l)
        lab = cv2.merge((l,a,b))
        enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
        return enhanced

    def update_track_history(self, track_id, position, current_time):
        history = self.track_history[track_id]
        history['positions'].append(position)
        
        # محدود کردن تاریخچه به ۳۰ فریم
        if len(history['positions']) > 30:
            history['positions'].pop(0)
        
        history['time'] = current_time

    def draw_ultra_stable_symbol(self, frame, track_id):
        history = self.track_history.get(track_id)
        if not history or len(history['positions']) == 0:
            return
            
        # محاسبه میانگین وزنی با توجه به زمان
        positions = np.array(history['positions'])
        weights = np.linspace(0.1, 1, len(positions))
        avg_pos = np.average(positions, axis=0, weights=weights)
        center = (int(avg_pos[0]), int(avg_pos[1]))
        
        # اندازه پویا
        height, width = frame.shape[:2]
        size = max(20, int(min(height, width) * 0.05))
        
        # رسم علامت
        symbol_idx = (track_id - 1) % 3
        if symbol_idx == 0:
            self.draw_plus(frame, center, size)
        elif symbol_idx == 1:
            self.draw_minus(frame, center, size)
        else:
            self.draw_cross(frame, center, size)

    def draw_plus(self, frame, center, size):
        color = self.colors[0]
        thickness = max(3, size // 8)
        
        # رسم با anti-aliasing
        cv2.line(frame, (center[0]-size, center[1]), 
                (center[0]+size, center[1]), color, thickness, cv2.LINE_AA)
        cv2.line(frame, (center[0], center[1]-size), 
                (center[0], center[1]+size), color, thickness, cv2.LINE_AA)

    def draw_minus(self, frame, center, size):
        color = self.colors[1]
        thickness = max(3, size // 8)
        cv2.line(frame, (center[0]-size, center[1]), 
                (center[0]+size, center[1]), color, thickness, cv2.LINE_AA)

    def draw_cross(self, frame, center, size):
        color = self.colors[2]
        thickness = max(3, size // 8)
        cv2.line(frame, (center[0]-size, center[1]-size), 
                (center[0]+size, center[1]+size), color, thickness, cv2.LINE_AA)
        cv2.line(frame, (center[0]+size, center[1]-size), 
                (center[0]-size, center[1]+size), color, thickness, cv2.LINE_AA)

# دیکشنری برای ذخیره وضعیت پردازش
processing_status = {}

def process_video_heavy(input_path, output_path, filename):
    try:
        processing_status[filename] = {
            'status': 'processing',
            'progress': 0,
            'message': 'در حال پردازش...'
        }
        
        cap = cv2.VideoCapture(input_path)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = int(cap.get(cv2.CAP_PROP_FPS))
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        
        # کدک برای حفظ کیفیت فوق‌العاده
        fourcc = cv2.VideoWriter_fourcc(*'avc1')
        out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
        
        tracker = UltraHeavyTracker()
        frame_count = 0
        
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break
                
            processed_frame = tracker.process_frame(frame)
            out.write(processed_frame)
            
            frame_count += 1
            progress = int((frame_count / total_frames) * 100)
            processing_status[filename]['progress'] = progress
            
            if frame_count % 10 == 0:
                processing_status[filename]['message'] = f'پردازش فریم {frame_count} از {total_frames}'
        
        cap.release()
        out.release()
        
        processing_status[filename] = {
            'status': 'completed',
            'progress': 100,
            'message': 'پردازش با موفقیت انجام شد'
        }
        
    except Exception as e:
        processing_status[filename] = {
            'status': 'error',
            'progress': 0,
            'message': f'خطا: {str(e)}'
        }

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/upload', methods=['POST'])
def upload_file():
    if 'video' not in request.files:
        return jsonify({'error': 'فایل ویدئویی ارسال نشده'}), 400
    
    file = request.files['video']
    if file.filename == '':
        return jsonify({'error': 'هیچ فایلی انتخاب نشده'}), 400
    
    if not file.filename.lower().endswith(('.mp4', '.avi', '.mov', '.mkv')):
        return jsonify({'error': 'فرمت فایل نامعتبر'}), 400
    
    # ذخیره فایل با نام منحصر به فرد
    filename = f"{int(time.time())}_{file.filename}"
    upload_path = os.path.join(UPLOAD_FOLDER, filename)
    file.save(upload_path)
    
    # مسیر خروجی
    output_path = os.path.join(PROCESSED_FOLDER, f'processed_{filename}')
    
    # شروع پردازش در یک رشته جداگانه
    thread = threading.Thread(
        target=process_video_heavy,
        args=(upload_path, output_path, filename)
    )
    thread.start()
    
    return jsonify({
        'filename': filename,
        'message': 'پردازش شروع شد'
    })

@app.route('/progress/<filename>')
def get_progress(filename):
    status = processing_status.get(filename, {
        'status': 'unknown',
        'progress': 0,
        'message': 'وضعیت نامعلوم'
    })
    return jsonify(status)

@app.route('/processed/<filename>')
def processed_file(filename):
    return send_from_directory(PROCESSED_FOLDER, f'processed_{filename}')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, threaded=True)
