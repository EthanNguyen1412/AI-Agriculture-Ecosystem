"""
YOLOv8 Training Script - Clean Dataset v1
Huấn luyện mô hình Object Detection với chiến lược chống Overfitting
Hardware: RTX 5060, 32GB RAM
Dataset: 7 classes (0-5: bệnh, 6: garbage)
"""

from ultralytics import YOLO
import torch
import os
from pathlib import Path


def print_system_info():
    """In thông tin hệ thống và cấu hình"""
    print("=" * 60)
    print("THÔNG TIN HỆ THỐNG")
    print("=" * 60)
    print(f"PyTorch version: {torch.__version__}")
    print(f"CUDA available: {torch.cuda.is_available()}")
    if torch.cuda.is_available():
        print(f"CUDA version: {torch.version.cuda}")
        print(f"GPU device: {torch.cuda.get_device_name(0)}")
        print(f"GPU memory: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.2f} GB")
    print("=" * 60)
    print()


def print_training_config(config):
    """In cấu hình huấn luyện"""
    print("=" * 60)
    print("CẤU HÌNH HUẤN LUYỆN")
    print("=" * 60)
    print(f"Model: {config['model']}")
    print(f"Dataset: {config['data']}")
    print(f"Epochs: {config['epochs']}")
    print(f"Batch size: {config['batch']}")
    print(f"Image size: {config['imgsz']}")
    print(f"Device: {config['device']}")
    print(f"Patience (Early stopping): {config['patience']}")
    print(f"Output directory: {config['project']}/{config['name']}")
    print()
    print("AUGMENTATION SETTINGS (Chống Overfitting):")
    print(f"  - Mosaic: {config['mosaic']}")
    print(f"  - Mixup: {config['mixup']}")
    print(f"  - Degrees (rotation): {config['degrees']}")
    print(f"  - HSV-H: {config['hsv_h']}")
    print(f"  - HSV-S: {config['hsv_s']}")
    print(f"  - HSV-V: {config['hsv_v']}")
    print(f"  - Translate: {config['translate']}")
    print(f"  - Scale: {config['scale']}")
    print(f"  - Flip (horizontal): {config['fliplr']}")
    print("=" * 60)
    print()


def validate_dataset(data_path):
    """Kiểm tra dataset có tồn tại không"""
    if not os.path.exists(data_path):
        raise FileNotFoundError(f"Dataset không tồn tại: {data_path}")
    print(f"✓ Dataset path validated: {data_path}")


def main():
    """Hàm chính để huấn luyện mô hình"""
    
    # In thông tin hệ thống
    print_system_info()
    
    # Cấu hình huấn luyện
    config = {
        # Model & Dataset
        'model': 'yolov8m.pt',  # Medium model - cân bằng giữa speed và accuracy
        'data': './dataset_v1_clean/data.yaml',
        
        # Training parameters
        'epochs': 150,
        'batch': 32,  # Tận dụng 32GB RAM
        'imgsz': 640,
        'device': 0,  # GPU 0
        'patience': 35,  # Kiên nhẫn hơn để đạt mAP ổn định
        
        # Output
        'project': 'runs/train',
        'name': 'pretrain_v1_clean',
        'exist_ok': False,  # Không ghi đè thư mục cũ
        
        # Augmentation mạnh (Chống Overfitting)
        'mosaic': 0.8,  # Giảm nhẹ để tránh tạo ảnh quá "ảo"
        'mixup': 0.1,   # Giảm mixup để giữ đặc trưng bệnh rõ hơn
        'degrees': 10,  # Xoay vừa phải để sát dữ liệu thật
        'translate': 0.1,  # Dịch chuyển ±10%
        'scale': 0.4,   # Scale 0.6-1.4x
        'fliplr': 0.5,  # 50% flip ngang
        
        # Color augmentation
        'hsv_h': 0.015,  # Hue shift ±1.5%
        'hsv_s': 0.7,    # Saturation 0.3-1.7x
        'hsv_v': 0.4,    # Value 0.6-1.4x
        
        # Optimization
        'optimizer': 'SGD',  # SGD thường cho mAP cuối tốt với detection
        'lr0': 0.005,  # LR an toàn cho batch 32
        'lrf': 0.01,   # Final learning rate (lr0 * lrf)
        'momentum': 0.937,
        'weight_decay': 0.0005,
        'warmup_epochs': 5.0,
        'warmup_momentum': 0.8,
        'warmup_bias_lr': 0.1,
        
        # Other settings
        'workers': 8,  # Số workers cho DataLoader
        'save': True,
        'save_period': -1,  # Chỉ save best và last
        'cache': False,  # Không cache để tránh hết RAM
        'rect': False,  # Rectangular training (tắt để tăng augmentation)
        'cos_lr': True,  # Cosine learning rate scheduler
        'close_mosaic': 15,  # Tắt mosaic sớm hơn để hội tụ trên ảnh thật
        'val': True,  # Validation sau mỗi epoch
        'plots': True,  # Vẽ biểu đồ
        'seed': 42,
        'deterministic': True,
    }
    
    # Kiểm tra dataset
    validate_dataset(config['data'])
    
    # In cấu hình
    print_training_config(config)
    
    # Tạo output directory nếu chưa có
    output_path = Path(config['project']) / config['name']
    print(f"Output sẽ được lưu tại: {output_path.absolute()}")
    print()
    
    # Khởi tạo model
    print("=" * 60)
    print("KHỞI TẠO MODEL")
    print("=" * 60)
    model = YOLO(config['model'])
    print(f"✓ Model {config['model']} đã được load thành công")
    print()
    
    # Bắt đầu training
    print("=" * 60)
    print("BẮT ĐẦU HUẤN LUYỆN")
    print("=" * 60)
    print()
    
    try:
        results = model.train(
            data=config['data'],
            epochs=config['epochs'],
            batch=config['batch'],
            imgsz=config['imgsz'],
            device=config['device'],
            patience=config['patience'],
            project=config['project'],
            name=config['name'],
            exist_ok=config['exist_ok'],
            
            # Augmentation
            mosaic=config['mosaic'],
            mixup=config['mixup'],
            degrees=config['degrees'],
            translate=config['translate'],
            scale=config['scale'],
            fliplr=config['fliplr'],
            hsv_h=config['hsv_h'],
            hsv_s=config['hsv_s'],
            hsv_v=config['hsv_v'],
            
            # Optimization
            optimizer=config['optimizer'],
            lr0=config['lr0'],
            lrf=config['lrf'],
            momentum=config['momentum'],
            weight_decay=config['weight_decay'],
            warmup_epochs=config['warmup_epochs'],
            warmup_momentum=config['warmup_momentum'],
            warmup_bias_lr=config['warmup_bias_lr'],
            
            # Other
            workers=config['workers'],
            save=config['save'],
            save_period=config['save_period'],
            cache=config['cache'],
            rect=config['rect'],
            cos_lr=config['cos_lr'],
            close_mosaic=config['close_mosaic'],
            val=config['val'],
            plots=config['plots'],
            seed=config['seed'],
            deterministic=config['deterministic'],
        )
        
        print()
        print("=" * 60)
        print("HUẤN LUYỆN HOÀN TẤT")
        print("=" * 60)
        print(f"✓ Kết quả được lưu tại: {output_path.absolute()}")
        print(f"✓ Best model: {output_path / 'weights' / 'best.pt'}")
        print(f"✓ Last model: {output_path / 'weights' / 'last.pt'}")
        print()
        
        # In metrics cuối cùng
        if hasattr(results, 'results_dict'):
            print("METRICS CUỐI CÙNG:")
            metrics = results.results_dict
            if 'metrics/mAP50(B)' in metrics:
                print(f"  mAP50: {metrics['metrics/mAP50(B)']:.4f}")
            if 'metrics/mAP50-95(B)' in metrics:
                print(f"  mAP50-95: {metrics['metrics/mAP50-95(B)']:.4f}")
            print()
        
    except KeyboardInterrupt:
        print("\n" + "=" * 60)
        print("TRAINING BỊ DỪNG BỞI USER")
        print("=" * 60)
        print(f"Checkpoint được lưu tại: {output_path / 'weights' / 'last.pt'}")
        
    except Exception as e:
        print("\n" + "=" * 60)
        print("LỖI XẢY RA")
        print("=" * 60)
        print(f"Error: {str(e)}")
        raise


if __name__ == "__main__":
    main()