import os
import cv2
import numpy as np
import torch
from ultralytics import YOLO
from flask import Flask, render_template, request, jsonify, send_from_directory
from collections import defaultdict
import time
import threading
from werkzeug.utils import secure_filename

# تنظیمات برنامه
app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = 'uploads'
app.config['PROCESSED_FOLDER'] = 'processed'
app.config['MAX_CONTENT_LENGTH'] = 2 * 1024 * 1024 * 1024  # 2GB
app.config['ALLOWED_EXTENSIONS'] = {'mp4', 'avi', 'mov', 'mkv'}

# ایجاد دایرکتوری‌های مورد نیاز
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)
os.makedirs(app.config['PROCESSED_FOLDER'], exist_ok=True)

# بارگذاری مدل
device = 'cuda' if torch.cuda.is_available() else 'cpu'
model = YOLO('yolov8n.pt').to(device)

# کلاس ردیابی پیشرفته
class UltraBowlTracker:
    def __init__(self):
        self.track_history = defaultdict(lambda: {
            'positions': [],
            'timestamps': [],
            'avg_position': None
        })
        self.symbols = ['➕', '➖', '✖️']
        self.colors = [
            (0, 255, 0),   # سبز
            (0, 0, 255),    # قرمز
            (255, 0, 0)     # آبی
        ]
        self.last_processed = time.time()

    def process_frame(self, frame):
        try:
            # پیش‌پردازش تصویر
            enhanced = self.enhance_image(frame)
            
            # ردیابی با YOLOv8 + ByteTrack
            results = model.track(
                enhanced,
                persist=True,
                tracker="bytetrack.yaml",
                imgsz=640,
                conf=0.7,
                device=device,
                verbose=False
            )

            if results[0].boxes.id is None:
                return enhanced

            # پردازش نتایج ردیابی
            boxes = results[0].boxes.xywh.cpu()
            track_ids = results[0].boxes.id.int().cpu().tolist()
            current_time = time.time()

            # فقط ۳ شیء اول را نگه دار
            active_tracks = []
            for box, track_id in zip(boxes, track_ids):
                if track_id <= 3:
                    active_tracks.append((box, track_id))

            # مرتب‌سازی بر اساس موقعیت X
            active_tracks.sort(key=lambda x: x[0][0])

            for box, track_id in active_tracks:
                x, y, w, h = box
                center = (int(x), int(y))
                
                # به‌روزرسانی تاریخچه ردیابی
                self.update_track_history(track_id, center, current_time)
                
                # رسم علامت با ثبات فوق‌العاده
                self.draw_stable_symbol(enhanced, track_id)

            return enhanced

        except Exception as e:
            app.logger.error(f'Error processing frame: {str(e)}')
            return frame

    def enhance_image(self, frame):
        """افزایش کیفیت تصویر ورودی"""
        # افزایش کنتراست با CLAHE
        lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8,8))
        l = clahe.apply(l)
        lab = cv2.merge((l,a,b))
        enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
        
        # کاهش نویز
        enhanced = cv2.fastNlMeansDenoisingColored(enhanced, None, 10, 10, 7, 21)
        return enhanced

    def update_track_history(self, track_id, position, timestamp):
        """به‌روزرسانی تاریخچه ردیابی با فیلتر کالمن"""
        history = self.track_history[track_id]
        history['positions'].append(position)
        history['timestamps'].append(timestamp)
        
        # محدود کردن تاریخچه به ۳۰ فریم
        if len(history['positions']) > 30:
            history['positions'].pop(0)
            history['timestamps'].pop(0)
        
        # محاسبه میانگین وزنی
        if len(history['positions']) > 0:
            weights = np.linspace(0.1, 1, len(history['positions']))
            weights = weights / np.sum(weights)
            avg_x = int(np.dot([p[0] for p in history['positions']], weights))
            avg_y = int(np.dot([p[1] for p in history['positions']], weights))
            history['avg_position'] = (avg_x, avg_y)

    def draw_stable_symbol(self, frame, track_id):
        """رسم علامت با ثبات فوق‌العاده"""
        history = self.track_history.get(track_id)
        if not history or not history['avg_position']:
            return
            
        center = history['avg_position']
        symbol_idx = (track_id - 1) % 3
        
        # اندازه پویا بر اساس ابعاد فریم
        height, width = frame.shape[:2]
        size = max(20, int(min(height, width) * 0.05))
        thickness = max(3, size // 8)
        
        # رسم علامت
        if symbol_idx == 0:
            self.draw_plus(frame, center, size, thickness)
        elif symbol_idx == 1:
            self.draw_minus(frame, center, size, thickness)
        else:
            self.draw_cross(frame, center, size, thickness)

    def draw_plus(self, frame, center, size, thickness):
        cv2.line(frame, 
                (center[0]-size, center[1]), 
                (center[0]+size, center[1]), 
                self.colors[0], thickness, cv2.LINE_AA)
        cv2.line(frame,
                (center[0], center[1]-size),
                (center[0], center[1]+size),
                self.colors[0], thickness, cv2.LINE_AA)

    def draw_minus(self, frame, center, size, thickness):
        cv2.line(frame,
                (center[0]-size, center[1]),
                (center[0]+size, center[1]),
                self.colors[1], thickness, cv2.LINE_AA)

    def draw_cross(self, frame, center, size, thickness):
        cv2.line(frame,
                (center[0]-size, center[1]-size),
                (center[0]+size, center[1]+size),
                self.colors[2], thickness, cv2.LINE_AA)
        cv2.line(frame,
                (center[0]+size, center[1]-size),
                (center[0]-size, center[1]+size),
                self.colors[2], thickness, cv2.LINE_AA)

# وضعیت پردازش
processing_status = {}

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in app.config['ALLOWED_EXTENSIONS']

def process_video_task(input_path, output_path, filename):
    """وظیفه پردازش ویدئو در پس‌زمینه"""
    try:
        # تنظیم وضعیت اولیه
        processing_status[filename] = {
            'status': 'processing',
            'progress': 0,
            'message': 'در حال آماده‌سازی...',
            'start_time': time.time()
        }

        cap = cv2.VideoCapture(input_path)
        if not cap.isOpened():
            raise ValueError("نمیتوان ویدئو را باز کرد")

        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = cap.get(cv2.CAP_PROP_FPS)
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

        # تنظیم کدک ویدئوی خروجی
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))

        tracker = UltraBowlTracker()
        frame_count = 0

        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break

            # پردازش فریم
            processed_frame = tracker.process_frame(frame)
            out.write(processed_frame)

            frame_count += 1
            progress = int((frame_count / total_frames) * 100)
            
            # به‌روزرسانی وضعیت
            processing_status[filename].update({
                'progress': progress,
                'message': f'پردازش فریم {frame_count} از {total_frames}',
                'current_frame': frame_count,
                'total_frames': total_frames
            })

        # آزادسازی منابع
        cap.release()
        out.release()

        # محاسبه زمان پردازش
        processing_time = time.time() - processing_status[filename]['start_time']
        
        # به‌روزرسانی وضعیت نهایی
        processing_status[filename].update({
            'status': 'completed',
            'progress': 100,
            'message': 'پردازش با موفقیت انجام شد',
            'processing_time': processing_time
        })

    except Exception as e:
        processing_status[filename] = {
            'status': 'error',
            'message': f'خطا در پردازش: {str(e)}',
            'error': str(e)
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
    
    if not allowed_file(file.filename):
        return jsonify({'error': 'فرمت فایل نامعتبر'}), 400

    try:
        # ایجاد نام فایل امن
        timestamp = int(time.time())
        filename = f"{timestamp}_{secure_filename(file.filename)}"
        upload_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        
        # ذخیره فایل
        file.save(upload_path)
        
        # بررسی ذخیره‌سازی
        if not os.path.exists(upload_path):
            return jsonify({'error': 'خطا در ذخیره فایل'}), 500

        # مسیر خروجی
        output_path = os.path.join(app.config['PROCESSED_FOLDER'], f'processed_{filename}')
        
        # شروع پردازش در رشته جدید
        thread = threading.Thread(
            target=process_video_task,
            args=(upload_path, output_path, filename)
        )
        thread.start()
        
        return jsonify({
            'status': 'success',
            'filename': filename,
            'message': 'پردازش ویدئو شروع شد'
        })

    except Exception as e:
        app.logger.error(f'خطا در آپلود: {str(e)}')
        return jsonify({'error': f'خطای سرور: {str(e)}'}), 500

@app.route('/progress/<filename>')
def get_progress(filename):
    status = processing_status.get(filename, {
        'status': 'not_found',
        'message': 'ویدئو یافت نشد'
    })
    return jsonify(status)

@app.route('/processed/<filename>')
def download_processed(filename):
    try:
        return send_from_directory(
            app.config['PROCESSED_FOLDER'],
            f'processed_{filename}',
            as_attachment=True
        )
    except FileNotFoundError:
        return jsonify({'error': 'فایل پردازش شده یافت نشد'}), 404

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, threaded=True)
