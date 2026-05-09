"""
Advanced Dataset Preparation Script for YOLOv8
Xử lý dữ liệu Classification (folder-based) → Object Detection (YOLO format)
Auto-labeling: Tạo bounding box toàn ảnh cho mỗi sample
Author: Data Engineer Team
Version: 1.0
"""

import yaml
import shutil
from pathlib import Path
from typing import Dict, List, Tuple, Optional
from collections import defaultdict
import random
from PIL import Image
from tqdm import tqdm
import sys


class DatasetPreparator:
    """Class chính để chuẩn bị dataset cho YOLOv8"""
    
    def __init__(self, config_path: str = "config_v1.yaml"):
        """
        Khởi tạo DatasetPreparator
        
        Args:
            config_path: Đường dẫn đến file config YAML
        """
        self.config_path = Path(config_path)
        self.config = self._load_config()
        self.stats = defaultdict(int)
        self.class_stats = defaultdict(int)
        
        # Parse config
        self.raw_data_path = Path(self.config['paths']['raw_data'])
        self.output_path = Path(self.config['paths']['output'])
        self.train_split = self.config['split']['train']
        self.val_split = self.config['split']['val']
        self.seed = self.config['split']['seed']
        
        # Label mapping
        self.label_mapping = self.config['label_mapping']
        self.alias_to_class_id = self._build_alias_mapping()
        
        # Bbox settings
        self.bbox_config = self.config['label_generation']['default_bbox']
        
        # Processing settings
        self.copy_method = self.config['processing']['copy_method']
        self.overwrite = self.config['processing']['overwrite_existing']
        
        # Set random seed
        random.seed(self.seed)
        
    def _load_config(self) -> Dict:
        """Load configuration từ file YAML"""
        if not self.config_path.exists():
            raise FileNotFoundError(f"❌ Config file không tồn tại: {self.config_path}")
        
        with open(self.config_path, 'r', encoding='utf-8') as f:
            config = yaml.safe_load(f)
        
        print(f"✓ Đã load config từ: {self.config_path}")
        return config
    
    def _build_alias_mapping(self) -> Dict[str, int]:
        """
        Xây dựng mapping từ alias (tên thư mục) → class_id
        
        Returns:
            Dictionary: {alias: class_id}
        """
        alias_map = {}
        for class_id, info in self.label_mapping.items():
            for alias in info['aliases']:
                alias_map[alias.lower()] = int(class_id)
        
        return alias_map
    
    def _validate_image(self, image_path: Path) -> bool:
        """
        Kiểm tra ảnh có hợp lệ không
        
        Args:
            image_path: Đường dẫn đến ảnh
            
        Returns:
            True nếu ảnh hợp lệ, False nếu lỗi
        """
        try:
            with Image.open(image_path) as img:
                img.verify()  # Verify image integrity
            return True
        except Exception as e:
            self.stats['invalid_images'] += 1
            return False
    
    def _get_class_id_from_path(self, image_path: Path) -> Optional[int]:
        """
        Xác định class_id từ đường dẫn ảnh (dựa vào tên thư mục cha)
        
        Args:
            image_path: Đường dẫn đến ảnh
            
        Returns:
            class_id hoặc None nếu không match
        """
        # Lấy tên thư mục cha (immediate parent)
        folder_name = image_path.parent.name.lower()
        
        # Tìm trong alias mapping
        if folder_name in self.alias_to_class_id:
            return self.alias_to_class_id[folder_name]
        
        # Thử tìm trong tên đường dẫn đầy đủ (recursive check)
        for part in image_path.parts:
            part_lower = part.lower()
            if part_lower in self.alias_to_class_id:
                return self.alias_to_class_id[part_lower]
        
        return None
    
    def _create_label_file(self, class_id: int, output_label_path: Path) -> None:
        """
        Tạo file label (.txt) với bounding box toàn ảnh
        
        Args:
            class_id: ID của class
            output_label_path: Đường dẫn output cho file label
        """
        if not self.bbox_config['enabled']:
            return
        
        # Format: class_id x_center y_center width height (normalized 0-1)
        x_center = self.bbox_config['x_center']
        y_center = self.bbox_config['y_center']
        width = self.bbox_config['width']
        height = self.bbox_config['height']
        
        label_content = f"{class_id} {x_center} {y_center} {width} {height}\n"
        
        with open(output_label_path, 'w') as f:
            f.write(label_content)
    
    def _scan_images(self) -> List[Path]:
        """
        Quét đệ quy tất cả ảnh trong thư mục raw data
        
        Returns:
            List các đường dẫn ảnh
        """
        print("\n" + "=" * 70)
        print("QUÉT DỮ LIỆU")
        print("=" * 70)
        
        if not self.raw_data_path.exists():
            raise FileNotFoundError(f"❌ Thư mục raw data không tồn tại: {self.raw_data_path}")
        
        # Supported image extensions
        image_extensions = {'.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.webp'}
        
        image_paths = []
        for ext in image_extensions:
            image_paths.extend(self.raw_data_path.rglob(f"*{ext}"))
            image_paths.extend(self.raw_data_path.rglob(f"*{ext.upper()}"))
        
        print(f"✓ Tìm thấy {len(image_paths)} ảnh trong {self.raw_data_path}")
        return image_paths
    
    def _split_dataset(self, image_paths: List[Path]) -> Tuple[List[Path], List[Path]]:
        """
        Chia dataset thành train và val sets
        
        Args:
            image_paths: List các đường dẫn ảnh
            
        Returns:
            Tuple (train_paths, val_paths)
        """
        # Shuffle để đảm bảo random
        random.shuffle(image_paths)
        
        # Tính toán split point
        total = len(image_paths)
        train_count = int(total * self.train_split)
        
        train_paths = image_paths[:train_count]
        val_paths = image_paths[train_count:]
        
        print(f"\n📊 CHIA DỮ LIỆU:")
        print(f"  Train: {len(train_paths)} ảnh ({self.train_split*100:.0f}%)")
        print(f"  Val:   {len(val_paths)} ảnh ({self.val_split*100:.0f}%)")
        
        return train_paths, val_paths
    
    def _process_split(self, image_paths: List[Path], split_name: str) -> None:
        """
        Xử lý một split (train hoặc val)
        
        Args:
            image_paths: List các đường dẫn ảnh
            split_name: 'train' hoặc 'val'
        """
        # Tạo thư mục output
        images_dir = self.output_path / split_name / 'images'
        labels_dir = self.output_path / split_name / 'labels'
        images_dir.mkdir(parents=True, exist_ok=True)
        labels_dir.mkdir(parents=True, exist_ok=True)
        
        # Process từng ảnh
        print(f"\n🔄 Xử lý {split_name} set:")
        
        for image_path in tqdm(image_paths, desc=f"  {split_name.upper()}", unit="img"):
            try:
                # Validate image
                if not self._validate_image(image_path):
                    continue
                
                # Xác định class_id
                class_id = self._get_class_id_from_path(image_path)
                if class_id is None:
                    self.stats['unmapped_images'] += 1
                    continue
                
                # Tạo tên file output (unique)
                stem = image_path.stem
                suffix = image_path.suffix
                output_image_name = f"{stem}{suffix}"
                
                # Đảm bảo tên file unique (nếu trùng, thêm suffix)
                counter = 1
                while (images_dir / output_image_name).exists():
                    output_image_name = f"{stem}_{counter}{suffix}"
                    counter += 1
                
                output_image_path = images_dir / output_image_name
                output_label_path = labels_dir / f"{Path(output_image_name).stem}.txt"
                
                # Copy image
                if self.copy_method == "copy":
                    shutil.copy2(image_path, output_image_path)
                else:  # move
                    shutil.move(str(image_path), str(output_image_path))
                
                # Tạo label file (auto-labeling)
                self._create_label_file(class_id, output_label_path)
                
                # Update statistics
                self.stats[f'{split_name}_images'] += 1
                self.class_stats[class_id] += 1
                
            except Exception as e:
                self.stats['error_images'] += 1
                print(f"\n⚠️  Lỗi xử lý {image_path.name}: {e}")
                continue
    
    def _create_data_yaml(self) -> None:
        """Tạo file data.yaml cho YOLOv8"""
        print("\n" + "=" * 70)
        print("TẠO FILE DATA.YAML")
        print("=" * 70)
        
        # Build class names list (sorted by class_id)
        class_names = []
        for class_id in sorted([int(k) for k in self.label_mapping.keys()]):
            standard_name = self.label_mapping[class_id]['standard_name']
            class_names.append(standard_name)
        
        # Create YAML structure
        data_yaml = {
            'path': str(self.output_path.absolute()),  # Root path
            'train': 'train/images',  # Relative to path
            'val': 'val/images',      # Relative to path
            'nc': len(class_names),   # Number of classes
            'names': class_names      # Class names
        }
        
        # Write to file
        yaml_path = self.output_path / 'data.yaml'
        with open(yaml_path, 'w', encoding='utf-8') as f:
            yaml.dump(data_yaml, f, default_flow_style=False, allow_unicode=True, sort_keys=False)
        
        print(f"✓ Đã tạo file: {yaml_path}")
        print(f"✓ Số classes: {len(class_names)}")
        print(f"✓ Class names: {class_names}")
    
    def _print_statistics(self) -> None:
        """In thống kê cuối cùng"""
        print("\n" + "=" * 70)
        print("📊 THỐNG KÊ CUỐI CÙNG")
        print("=" * 70)
        
        # Overall stats
        print("\nTổng quan:")
        print(f"  ✓ Train images: {self.stats['train_images']}")
        print(f"  ✓ Val images: {self.stats['val_images']}")
        print(f"  ✓ Tổng cộng: {self.stats['train_images'] + self.stats['val_images']}")
        
        if self.stats['invalid_images'] > 0:
            print(f"  ⚠️  Ảnh lỗi (bỏ qua): {self.stats['invalid_images']}")
        if self.stats['unmapped_images'] > 0:
            print(f"  ⚠️  Ảnh không map được class: {self.stats['unmapped_images']}")
        if self.stats['error_images'] > 0:
            print(f"  ❌ Ảnh lỗi xử lý: {self.stats['error_images']}")
        
        # Class distribution
        print("\nPhân bố theo class:")
        for class_id in sorted(self.class_stats.keys()):
            count = self.class_stats[class_id]
            class_name = self.label_mapping[class_id]['standard_name']
            percentage = (count / (self.stats['train_images'] + self.stats['val_images'])) * 100
            print(f"  [{class_id}] {class_name:20s}: {count:5d} ảnh ({percentage:5.1f}%)")
        
        # Check class imbalance
        if self.class_stats:
            counts = list(self.class_stats.values())
            max_count = max(counts)
            min_count = min(counts)
            imbalance_ratio = max_count / min_count if min_count > 0 else float('inf')
            
            print(f"\n⚖️  Tỷ lệ mất cân bằng: {imbalance_ratio:.2f}x")
            if imbalance_ratio > 5:
                print("  ⚠️  CẢNH BÁO: Dataset mất cân bằng nghiêm trọng!")
                print("     Cân nhắc sử dụng:")
                print("     - Data augmentation cho class ít mẫu")
                print("     - Class weights trong training")
                print("     - Copy-paste augmentation")
        
        print("=" * 70)
    
    def _print_header(self) -> None:
        """In header của script"""
        print("\n" + "🔧" * 35)
        print("ADVANCED DATASET PREPARATION FOR YOLOv8")
        print("Auto-Labeling: Classification → Object Detection")
        print("🔧" * 35)
        
        print("\n📋 CẤU HÌNH:")
        print(f"  Input:  {self.raw_data_path}")
        print(f"  Output: {self.output_path}")
        print(f"  Split:  Train {self.train_split*100:.0f}% | Val {self.val_split*100:.0f}%")
        print(f"  Seed:   {self.seed}")
        print(f"  Classes: {len(self.label_mapping)}")
        
        # Print class mapping
        print("\n📝 CLASS MAPPING:")
        for class_id, info in sorted(self.label_mapping.items(), key=lambda x: int(x[0])):
            aliases = ', '.join(info['aliases'])
            print(f"  [{class_id}] {info['standard_name']:20s} ← {aliases}")
        
        # Print bbox config
        if self.bbox_config['enabled']:
            print("\n📦 AUTO-LABELING (Bounding Box):")
            print(f"  Center: ({self.bbox_config['x_center']}, {self.bbox_config['y_center']})")
            print(f"  Size:   {self.bbox_config['width']} x {self.bbox_config['height']}")
        
        print()
    
    def prepare(self) -> None:
        """Hàm chính để chuẩn bị dataset"""
        try:
            # Print header
            self._print_header()
            
            # Check và xóa output directory nếu overwrite
            if self.output_path.exists():
                if self.overwrite:
                    print(f"⚠️  Xóa thư mục output cũ: {self.output_path}")
                    shutil.rmtree(self.output_path)
                else:
                    print(f"❌ Thư mục output đã tồn tại: {self.output_path}")
                    print("   Set overwrite_existing: true trong config để ghi đè")
                    sys.exit(1)
            
            # Tạo thư mục output
            self.output_path.mkdir(parents=True, exist_ok=True)
            
            # Scan images
            image_paths = self._scan_images()
            if not image_paths:
                raise ValueError("❌ Không tìm thấy ảnh nào trong raw data!")
            
            # Split dataset
            train_paths, val_paths = self._split_dataset(image_paths)
            
            # Process train split
            self._process_split(train_paths, 'train')
            
            # Process val split
            self._process_split(val_paths, 'val')
            
            # Create data.yaml
            self._create_data_yaml()
            
            # Print statistics
            self._print_statistics()
            
            # Success message
            print("\n" + "✅" * 35)
            print("DATASET PREPARATION COMPLETED SUCCESSFULLY!")
            print("✅" * 35)
            print(f"\n📁 Output directory: {self.output_path.absolute()}")
            print(f"📄 Config file: data.yaml")
            print("\n💡 NEXT STEPS:")
            print("  1. Kiểm tra data.yaml")
            print("  2. Xem phân bố class (class distribution)")
            print("  3. Chạy training: python train_v1_clean.py")
            print()
            
        except Exception as e:
            print("\n" + "=" * 70)
            print("❌ LỖI XẢY RA")
            print("=" * 70)
            print(f"Error: {str(e)}")
            print("\n🔍 DEBUG CHECKLIST:")
            print("  1. Kiểm tra config_v1.yaml có đúng format không")
            print("  2. Kiểm tra RAW_DATA_CLEAN có tồn tại không")
            print("  3. Kiểm tra tên thư mục con có match với aliases không")
            print("  4. Verify quyền ghi vào thư mục output")
            raise


def main():
    """Entry point của script"""
    preparator = DatasetPreparator(config_path="config_v1.yaml")
    preparator.prepare()


if __name__ == "__main__":
    main()