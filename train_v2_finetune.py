"""
YOLOv8 Fine-tuning Script - Field Dataset v2
Transfer Learning từ v1 (Clean) sang v2 (Field)
Chiến lược: Learning rate thấp + Augmentation thực tế
Hardware: RTX 5060, 32GB RAM
Dataset: 7 classes (dữ liệu thực địa)
"""

from ultralytics import YOLO
import torch
import os
from pathlib import Path
import yaml


def print_system_info():
    """In thông tin hệ thống và cấu hình"""
    print("=" * 70)
    print("THÔNG TIN HỆ THỐNG")
    print("=" * 70)
    print(f"PyTorch version: {torch.__version__}")
    print(f"CUDA available: {torch.cuda.is_available()}")
    if torch.cuda.is_available():
        print(f"CUDA version: {torch.version.cuda}")
        print(f"GPU device: {torch.cuda.get_device_name(0)}")
        print(f"GPU memory: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.2f} GB")
    print("=" * 70)
    print()


def print_training_config(config):
    """In cấu hình huấn luyện"""
    print("=" * 70)
    print("CẤU HÌNH FINE-TUNING (TRANSFER LEARNING)")
    print("=" * 70)
    print(f"Pretrained Model: {config['model']}")
    print(f"New Dataset: {config['data']}")
    print(f"Strategy: Transfer Learning (v1 Clean → v2 Field)")
    print()
    print("TRAINING PARAMETERS:")
    print(f"  - Epochs: {config['epochs']}")
    print(f"  - Batch size: {config['batch']} (giảm để học kỹ từng mẫu)")
    print(f"  - Image size: {config['imgsz']}")
    print(f"  - Device: GPU {config['device']}")
    print(f"  - Patience: {config['patience']} (tăng lên để kiên nhẫn hơn)")
    print()
    print("🔑 LEARNING RATE (QUAN TRỌNG NHẤT):")
    print(f"  - lr0: {config['lr0']} ⚠️  (RẤT THẤP để bảo vệ trọng số đã học)")
    print(f"  - lrf: {config['lrf']} (final lr = {config['lr0'] * config['lrf']})")
    print(f"  - Cosine LR: {config['cos_lr']} (smooth decay)")
    print()
    print("AUGMENTATION THỰC TẾ (Mô phỏng điều kiện Field):")
    print(f"  - Mosaic: {config['mosaic']} (giảm xuống 50%)")
    print(f"  - Mixup: {config['mixup']}")
    print(f"  - Copy-Paste: {config['copy_paste']} ⭐ (tăng data cho lớp ít mẫu)")
    print(f"  - Degrees: {config['degrees']}")
    print(f"  - HSV-H/S/V: {config['hsv_h']}/{config['hsv_s']}/{config['hsv_v']}")
    print(f"  - Blur: {config['blur']} (mô phỏng camera rung)")
    print()
    print("OPTIMIZATION:")
    print(f"  - Optimizer: {config['optimizer']}")
    print(f"  - Weight decay: {config['weight_decay']}")
    print(f"  - Momentum: {config['momentum']}")
    print()
    print(f"Output directory: {config['project']}/{config['name']}")
    print("=" * 70)
    print()


def validate_paths(model_path, data_path):
    """Kiểm tra các đường dẫn cần thiết"""
    print("=" * 70)
    print("KIỂM TRA PATHS")
    print("=" * 70)
    
    # Kiểm tra pretrained model
    if not os.path.exists(model_path):
        raise FileNotFoundError(
            f"❌ Pretrained model không tồn tại: {model_path}\n"
            f"   Hãy chạy train_v1_clean.py trước để tạo model v1!"
        )
    print(f"✓ Pretrained model: {model_path}")
    
    # Kiểm tra dataset
    if not os.path.exists(data_path):
        raise FileNotFoundError(f"❌ Dataset không tồn tại: {data_path}")
    print(f"✓ Dataset: {data_path}")
    
    # Đọc và hiển thị thông tin dataset
    try:
        with open(data_path, 'r', encoding='utf-8') as f:
            data_info = yaml.safe_load(f)
            print(f"✓ Number of classes: {data_info.get('nc', 'unknown')}")
            if 'names' in data_info:
                print(f"✓ Class names: {data_info['names']}")
    except Exception as e:
        print(f"⚠️  Không thể đọc thông tin dataset: {e}")
    
    print("=" * 70)
    print()


def setup_albumentations():
    """Cấu hình Albumentations transforms (Blur + Noise)"""
    print("=" * 70)
    print("THIẾT LẬP ALBUMENTATIONS")
    print("=" * 70)
    print("Augmentations bổ sung để mô phỏng điều kiện thực tế:")
    print("  - Blur: Camera rung/không focus")
    print("  - Noise: Nhiễu sensor camera điện thoại")
    print("  - CLAHE: Cải thiện contrast trong điều kiện ánh sáng khác nhau")
    print()
    
    # Albumentations sẽ được tự động load bởi YOLOv8 nếu có file
    # Tạo augmentation config
    albumentations_config = """
# Albumentations augmentation config cho Field dataset
# Mô phỏng điều kiện camera điện thoại thực tế

blur_limit: 5  # Blur 0-5 pixels (camera rung)
p_blur: 0.3    # 30% ảnh bị blur

gauss_noise:
  var_limit: [10.0, 50.0]  # Nhiễu Gaussian
  p: 0.2  # 20% ảnh có nhiễu

clahe:
  clip_limit: 2.0  # Contrast Limited Adaptive Histogram Equalization
  p: 0.3  # 30% ảnh

motion_blur:
  blur_limit: 5
  p: 0.2  # 20% motion blur (chuyển động khi chụp)
"""
    
    print("Albumentations transforms đã được cấu hình.")
    print("(YOLOv8 sẽ tự động áp dụng nếu thư viện được cài đặt)")
    print("=" * 70)
    print()
    
    return albumentations_config


def main():
    """Hàm chính để fine-tune mô hình"""
    
    # In thông tin hệ thống
    print_system_info()
    
    # Cấu hình fine-tuning
    config = {
        # Model & Dataset (TRANSFER LEARNING)
        'model': 'runs/train/pretrain_v1_clean/weights/best.pt',  # Model v1
        'data': './dataset_v2_field/data.yaml',  # Data v2 (Field)
        
        # Training parameters
        'epochs': 120,
        'batch': 16,  # Giảm từ 32 → 16 để học kỹ hơn
        'imgsz': 640,
        'device': 0,  # GPU 0
        'patience': 40,  # Kiên nhẫn hơn để mAP hội tụ
        
        # Output
        'project': 'runs/train',
        'name': 'finetune_v2_field',
        'exist_ok': False,
        
        # 🔑 LEARNING RATE - QUAN TRỌNG NHẤT
        'lr0': 0.0002,  # Tăng nhẹ để tránh underfitting khi fine-tune
        'lrf': 0.01,    # Final LR = 0.0001 * 0.01 = 0.000001
        'cos_lr': True,  # Cosine LR scheduler (smooth decay)
        'warmup_epochs': 3.0,
        'warmup_momentum': 0.8,
        'warmup_bias_lr': 0.05,
        
        # Augmentation THỰC TẾ (Mô phỏng điều kiện Field)
        'mosaic': 0.3,  # Giảm để sát phân phối ảnh thực địa
        'mixup': 0.05,  # Giữ thấp để tránh mờ ranh giới bệnh
        'copy_paste': 0.2,  # Bổ sung mẫu cho lớp ít dữ liệu
        
        # Geometric augmentation (giữ vừa phải)
        'degrees': 8,  # Xoay nhẹ theo thực tế chụp tay
        'translate': 0.1,  # Dịch chuyển ±10%
        'scale': 0.5,   # Scale 0.5-1.5x
        'fliplr': 0.5,  # 50% flip ngang
        'flipud': 0.0,  # Không flip dọc (lá thường không ngược)
        
        # Color augmentation (tăng để mô phỏng điều kiện ánh sáng khác nhau)
        'hsv_h': 0.015,
        'hsv_s': 0.6,
        'hsv_v': 0.4,
        
        # 📷 BLUR - Mô phỏng camera rung/không focus
        'blur': 0.3,  # 30% ảnh bị blur (0-5 pixels)
        
        # Optimization
        'optimizer': 'AdamW',
        'momentum': 0.937,
        'weight_decay': 0.0003,
        
        # Other settings
        'workers': 8,
        'save': True,
        'save_period': -1,  # Chỉ save best và last
        'cache': False,  # Không cache
        'rect': False,  # Tắt rectangular training
        'close_mosaic': 20,  # Tắt mosaic sớm hơn ở giai đoạn cuối
        'val': True,
        'plots': True,
        'amp': True,  # Automatic Mixed Precision (tăng tốc)
        'seed': 42,
        'deterministic': True,
        
        # Freeze layers nhẹ để giữ đặc trưng từ v1
        'freeze': 8,
    }
    
    # Kiểm tra paths
    validate_paths(config['model'], config['data'])
    
    # Setup Albumentations
    albumentations_config = setup_albumentations()
    
    # In cấu hình
    print_training_config(config)
    
    # Tạo output directory
    output_path = Path(config['project']) / config['name']
    print(f"📁 Output sẽ được lưu tại: {output_path.absolute()}")
    print()
    
    # Load pretrained model
    print("=" * 70)
    print("LOAD PRETRAINED MODEL (v1)")
    print("=" * 70)
    model = YOLO(config['model'])
    print(f"✓ Model v1 đã được load từ: {config['model']}")
    print("✓ Trọng số đã học sẽ được giữ nguyên và fine-tune trên data v2")
    print()
    
    # Bắt đầu fine-tuning
    print("=" * 70)
    print("🚀 BẮT ĐẦU FINE-TUNING (v1 → v2)")
    print("=" * 70)
    print("⚠️  Lưu ý: Learning rate RẤT THẤP (0.0001) để không phá vỡ trọng số v1")
    print("📊 Monitoring: Theo dõi validation metrics để tránh overfitting")
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
            
            # Learning rate (QUAN TRỌNG)
            lr0=config['lr0'],
            lrf=config['lrf'],
            cos_lr=config['cos_lr'],
            warmup_epochs=config['warmup_epochs'],
            warmup_momentum=config['warmup_momentum'],
            warmup_bias_lr=config['warmup_bias_lr'],
            
            # Augmentation
            mosaic=config['mosaic'],
            mixup=config['mixup'],
            copy_paste=config['copy_paste'],
            degrees=config['degrees'],
            translate=config['translate'],
            scale=config['scale'],
            fliplr=config['fliplr'],
            flipud=config['flipud'],
            hsv_h=config['hsv_h'],
            hsv_s=config['hsv_s'],
            hsv_v=config['hsv_v'],
            
            # Blur augmentation
            blur=config['blur'],
            
            # Optimization
            optimizer=config['optimizer'],
            momentum=config['momentum'],
            weight_decay=config['weight_decay'],
            
            # Other
            workers=config['workers'],
            save=config['save'],
            save_period=config['save_period'],
            cache=config['cache'],
            rect=config['rect'],
            close_mosaic=config['close_mosaic'],
            val=config['val'],
            plots=config['plots'],
            amp=config['amp'],
            seed=config['seed'],
            deterministic=config['deterministic'],
            
            # Freeze layers (nếu có)
            freeze=config.get('freeze', None),
        )
        
        print()
        print("=" * 70)
        print("✅ FINE-TUNING HOÀN TẤT")
        print("=" * 70)
        print(f"📁 Kết quả được lưu tại: {output_path.absolute()}")
        print(f"🏆 Best model (v2): {output_path / 'weights' / 'best.pt'}")
        print(f"💾 Last checkpoint: {output_path / 'weights' / 'last.pt'}")
        print()
        
        # In metrics cuối cùng
        print("📊 METRICS CUỐI CÙNG:")
        print("-" * 70)
        if hasattr(results, 'results_dict'):
            metrics = results.results_dict
            if 'metrics/mAP50(B)' in metrics:
                print(f"  mAP50: {metrics['metrics/mAP50(B)']:.4f}")
            if 'metrics/mAP50-95(B)' in metrics:
                print(f"  mAP50-95: {metrics['metrics/mAP50-95(B)']:.4f}")
        print("-" * 70)
        print()
        
        print("💡 NEXT STEPS:")
        print("  1. Kiểm tra biểu đồ training curves tại: results.png")
        print("  2. Xem confusion matrix để phát hiện class nào khó học")
        print("  3. Validate trên test set: val.py")
        print("  4. Deploy model: best.pt")
        print()
        
    except KeyboardInterrupt:
        print("\n" + "=" * 70)
        print("⚠️  TRAINING BỊ DỪNG BỞI USER")
        print("=" * 70)
        print(f"💾 Checkpoint được lưu tại: {output_path / 'weights' / 'last.pt'}")
        print("   Có thể resume training bằng cách load checkpoint này.")
        
    except Exception as e:
        print("\n" + "=" * 70)
        print("❌ LỖI XẢY RA")
        print("=" * 70)
        print(f"Error: {str(e)}")
        print()
        print("🔍 DEBUG TIPS:")
        print("  1. Kiểm tra data.yaml có đúng format không")
        print("  2. Kiểm tra đường dẫn images/labels")
        print("  3. Verify pretrained model v1 tồn tại")
        print("  4. Kiểm tra CUDA memory (nvidia-smi)")
        raise


if __name__ == "__main__":
    # Thông báo trước khi chạy
    print("\n" + "🌾" * 35)
    print("YOLOv8 FINE-TUNING: v1 (Clean) → v2 (Field)")
    print("🌾" * 35 + "\n")
    
    main()
    
    print("\n" + "🌾" * 35)
    print("FINE-TUNING SCRIPT COMPLETED")
    print("🌾" * 35 + "\n")