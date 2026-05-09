"""
Advanced Dataset Preparation Script for YOLOv8 - Field Data (v2)
Xử lý multiple datasets với Class ID Remapping
Gộp nhiều bộ dataset con thành một dataset thống nhất
Author: Data Engineer Team
Version: 2.0
"""

import yaml
import shutil
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Set
from collections import defaultdict
from tqdm import tqdm
import sys


class FieldDatasetPreparator:
    """Class chính để chuẩn bị Field dataset với Class ID Remapping"""
    
    def __init__(self, config_path: str = "config_v2.yaml"):
        """
        Khởi tạo FieldDatasetPreparator
        
        Args:
            config_path: Đường dẫn đến file config YAML
        """
        self.config_path = Path(config_path)
        self.config = self._load_config()
        self.stats = defaultdict(int)
        self.class_stats = defaultdict(int)
        self.dataset_stats = defaultdict(lambda: defaultdict(int))
        
        # Parse config
        self.raw_data_path = Path(self.config['paths']['raw_data'])
        self.output_path = Path(self.config['paths']['output'])
        
        # Label mapping
        self.label_mapping = self.config['label_mapping']
        self.alias_to_class_id = self._build_alias_mapping()
        
        # Processing settings
        self.remap_enabled = self.config['label_generation']['existing_labels']['remap_class_ids']
        self.copy_method = self.config['processing']['copy_method']
        self.overwrite = self.config['processing']['overwrite_existing']
        
        # Store remapping info for each subdataset
        self.subdataset_mappings = {}
        
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
        Xây dựng mapping từ alias (tên lớp) → standard class_id
        
        Returns:
            Dictionary: {alias: standard_class_id}
        """
        alias_map = {}
        for class_id, info in self.label_mapping.items():
            standard_id = int(class_id)
            # Add standard name
            standard_name = info['standard_name'].lower()
            alias_map[standard_name] = standard_id
            # Add all aliases
            for alias in info['aliases']:
                alias_map[alias.lower()] = standard_id
        
        return alias_map
    
    def _find_subdatasets(self) -> List[Path]:
        """
        Tìm tất cả các subdataset (chứa file data.yaml)
        
        Returns:
            List các đường dẫn đến thư mục subdataset
        """
        print("\n" + "=" * 70)
        print("QUÉT SUBDATASETS")
        print("=" * 70)
        
        if not self.raw_data_path.exists():
            raise FileNotFoundError(f"❌ Thư mục raw data không tồn tại: {self.raw_data_path}")
        
        # Tìm tất cả file data.yaml
        yaml_files = list(self.raw_data_path.rglob("data.yaml"))
        
        # Lấy thư mục cha của mỗi data.yaml
        subdatasets = []
        for yaml_file in yaml_files:
            subdataset_dir = yaml_file.parent
            subdatasets.append(subdataset_dir)
            print(f"  ✓ Tìm thấy: {subdataset_dir.name}")
        
        if not subdatasets:
            raise ValueError(f"❌ Không tìm thấy subdataset nào trong {self.raw_data_path}")
        
        print(f"\n✓ Tổng cộng: {len(subdatasets)} subdatasets")
        return subdatasets
    
    def _load_subdataset_yaml(self, subdataset_path: Path) -> Optional[Dict]:
        """
        Load file data.yaml của subdataset
        
        Args:
            subdataset_path: Đường dẫn đến thư mục subdataset
            
        Returns:
            Dictionary hoặc None nếu lỗi
        """
        yaml_path = subdataset_path / "data.yaml"
        
        try:
            with open(yaml_path, 'r', encoding='utf-8') as f:
                data = yaml.safe_load(f)
            return data
        except Exception as e:
            print(f"⚠️  Không thể đọc {yaml_path}: {e}")
            return None
    
    def _build_class_mapping(self, subdataset_name: str, original_names: List[str]) -> Dict[int, int]:
        """
        Xây dựng mapping từ original_class_id → standard_class_id
        
        Args:
            subdataset_name: Tên subdataset (để log)
            original_names: List tên class gốc (index = original_class_id)
            
        Returns:
            Dictionary: {original_id: standard_id}
        """
        mapping = {}
        unmapped = []
        
        for original_id, class_name in enumerate(original_names):
            class_name_lower = class_name.lower().strip()
            
            # Tìm trong alias mapping
            if class_name_lower in self.alias_to_class_id:
                standard_id = self.alias_to_class_id[class_name_lower]
                mapping[original_id] = standard_id
            else:
                # Thử fuzzy matching (remove underscores, spaces)
                normalized_name = class_name_lower.replace('_', '').replace(' ', '')
                found = False
                
                for alias, std_id in self.alias_to_class_id.items():
                    normalized_alias = alias.replace('_', '').replace(' ', '')
                    if normalized_name == normalized_alias:
                        mapping[original_id] = std_id
                        found = True
                        break
                
                if not found:
                    unmapped.append((original_id, class_name))
                    self.stats['unmapped_classes'] += 1
        
        # Log mapping
        print(f"\n  📋 Class Mapping cho '{subdataset_name}':")
        for orig_id, std_id in sorted(mapping.items()):
            orig_name = original_names[orig_id]
            std_name = self.label_mapping[std_id]['standard_name']
            print(f"     [{orig_id}] {orig_name:25s} → [{std_id}] {std_name}")
        
        if unmapped:
            print(f"\n  ⚠️  UNMAPPED classes (sẽ bị bỏ qua):")
            for orig_id, class_name in unmapped:
                print(f"     [{orig_id}] {class_name}")
        
        return mapping
    
    def _remap_label_file(self, 
                          label_path: Path, 
                          class_mapping: Dict[int, int],
                          output_label_path: Path) -> bool:
        """
        Remap class IDs trong file label và lưu vào output
        
        Args:
            label_path: Đường dẫn file label gốc
            class_mapping: Dictionary mapping {original_id: standard_id}
            output_label_path: Đường dẫn output
            
        Returns:
            True nếu thành công, False nếu lỗi hoặc file rỗng
        """
        try:
            with open(label_path, 'r') as f:
                lines = f.readlines()
            
            if not lines:
                return False
            
            remapped_lines = []
            has_valid_box = False
            
            for line in lines:
                line = line.strip()
                if not line:
                    continue
                
                parts = line.split()
                if len(parts) < 5:  # class_id x y w h
                    continue
                
                try:
                    original_class_id = int(parts[0])
                    
                    # Remap class ID
                    if original_class_id in class_mapping:
                        standard_class_id = class_mapping[original_class_id]
                        remapped_line = f"{standard_class_id} {' '.join(parts[1:])}\n"
                        remapped_lines.append(remapped_line)
                        has_valid_box = True
                    else:
                        # Skip unmapped classes
                        self.stats['skipped_boxes'] += 1
                        continue
                        
                except (ValueError, IndexError):
                    continue
            
            if not has_valid_box:
                return False
            
            # Write remapped labels
            output_label_path.parent.mkdir(parents=True, exist_ok=True)
            with open(output_label_path, 'w') as f:
                f.writelines(remapped_lines)
            
            return True
            
        except Exception as e:
            print(f"\n⚠️  Lỗi remap {label_path.name}: {e}")
            return False
    
    def _get_image_path(self, label_path: Path, subdataset_path: Path) -> Optional[Path]:
        """
        Tìm đường dẫn ảnh tương ứng với file label
        
        Args:
            label_path: Đường dẫn file label
            subdataset_path: Thư mục gốc của subdataset
            
        Returns:
            Đường dẫn ảnh hoặc None
        """
        # Thử thay 'labels' → 'images'
        relative_path = label_path.relative_to(subdataset_path)
        
        # Thử nhiều patterns
        possible_patterns = [
            str(relative_path).replace('/labels/', '/images/'),
            str(relative_path).replace('\\labels\\', '\\images\\'),
        ]
        
        # Thử nhiều extensions
        image_extensions = ['.jpg', '.jpeg', '.png', '.JPG', '.JPEG', '.PNG']
        
        for pattern in possible_patterns:
            for ext in image_extensions:
                image_path = subdataset_path / pattern.replace('.txt', ext)
                if image_path.exists():
                    return image_path
        
        return None
    
    def _process_subdataset(self, subdataset_path: Path) -> None:
        """
        Xử lý một subdataset
        
        Args:
            subdataset_path: Đường dẫn đến thư mục subdataset
        """
        subdataset_name = subdataset_path.name
        
        print("\n" + "=" * 70)
        print(f"XỬ LÝ SUBDATASET: {subdataset_name}")
        print("=" * 70)
        
        # Load data.yaml
        subdataset_config = self._load_subdataset_yaml(subdataset_path)
        if not subdataset_config:
            print(f"⚠️  Bỏ qua subdataset: {subdataset_name}")
            return
        
        # Get original class names
        original_names = subdataset_config.get('names', [])
        if not original_names:
            print(f"⚠️  Không tìm thấy 'names' trong data.yaml")
            return
        
        print(f"✓ Classes gốc: {len(original_names)}")
        
        # Build class mapping
        class_mapping = self._build_class_mapping(subdataset_name, original_names)
        
        if not class_mapping:
            print(f"⚠️  Không có class nào được map, bỏ qua subdataset")
            return
        
        # Store mapping
        self.subdataset_mappings[subdataset_name] = class_mapping
        
        # Tìm tất cả label files (train + valid)
        label_paths = []
        for split in ['train', 'valid', 'val', 'test']:
            labels_dir = subdataset_path / split / 'labels'
            if labels_dir.exists():
                label_paths.extend(labels_dir.rglob("*.txt"))
        
        if not label_paths:
            print(f"⚠️  Không tìm thấy label files")
            return
        
        print(f"\n🔄 Xử lý {len(label_paths)} label files:")
        
        # Output directories
        output_images_dir = self.output_path / 'train' / 'images'
        output_labels_dir = self.output_path / 'train' / 'labels'
        output_images_dir.mkdir(parents=True, exist_ok=True)
        output_labels_dir.mkdir(parents=True, exist_ok=True)
        
        # Process each label file
        processed = 0
        skipped = 0
        
        for label_path in tqdm(label_paths, desc=f"  {subdataset_name}", unit="file"):
            try:
                # Tìm ảnh tương ứng
                image_path = self._get_image_path(label_path, subdataset_path)
                
                if not image_path:
                    skipped += 1
                    continue
                
                # Tạo tên file unique
                stem = image_path.stem
                suffix = image_path.suffix
                
                # Add subdataset prefix để tránh conflict
                unique_name = f"{subdataset_name}_{stem}"
                
                # Ensure unique
                counter = 1
                while (output_images_dir / f"{unique_name}{suffix}").exists():
                    unique_name = f"{subdataset_name}_{stem}_{counter}"
                    counter += 1
                
                output_image_path = output_images_dir / f"{unique_name}{suffix}"
                output_label_path = output_labels_dir / f"{unique_name}.txt"
                
                # Remap labels
                if self._remap_label_file(label_path, class_mapping, output_label_path):
                    # Copy image
                    if self.copy_method == "copy":
                        shutil.copy2(image_path, output_image_path)
                    else:
                        shutil.move(str(image_path), str(output_image_path))
                    
                    processed += 1
                    self.dataset_stats[subdataset_name]['processed'] += 1
                    
                    # Count boxes per class
                    with open(output_label_path, 'r') as f:
                        for line in f:
                            parts = line.strip().split()
                            if len(parts) >= 5:
                                class_id = int(parts[0])
                                self.class_stats[class_id] += 1
                else:
                    skipped += 1
                    self.dataset_stats[subdataset_name]['skipped'] += 1
                    
            except Exception as e:
                skipped += 1
                self.stats['error_files'] += 1
                continue
        
        print(f"\n  ✓ Processed: {processed} | ⚠️  Skipped: {skipped}")
        self.stats['total_processed'] += processed
        self.stats['total_skipped'] += skipped
    
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
        # QUAN TRỌNG: Trỏ cả train và val về cùng thư mục để YOLO tự split
        data_yaml = {
            'path': str(self.output_path.absolute()),
            'train': 'train/images',  # YOLO sẽ tự split trong training
            'val': 'train/images',    # Trỏ cùng chỗ để tránh lỗi
            'nc': len(class_names),
            'names': class_names
        }
        
        # Write to file
        yaml_path = self.output_path / 'data.yaml'
        with open(yaml_path, 'w', encoding='utf-8') as f:
            yaml.dump(data_yaml, f, default_flow_style=False, allow_unicode=True, sort_keys=False)
        
        print(f"✓ Đã tạo file: {yaml_path}")
        print(f"✓ Số classes: {len(class_names)}")
        print(f"✓ Class names: {class_names}")
        print()
        print("⚠️  LÀM RÕ: train và val đều trỏ về 'train/images'")
        print("   YOLOv8 sẽ tự động split trong quá trình training")
    
    def _print_statistics(self) -> None:
        """In thống kê chi tiết"""
        print("\n" + "=" * 70)
        print("📊 THỐNG KÊ CHI TIẾT")
        print("=" * 70)
        
        # Overall stats
        print("\n1️⃣  TỔNG QUAN:")
        print(f"  ✓ Tổng files processed: {self.stats['total_processed']}")
        print(f"  ⚠️  Tổng files skipped: {self.stats['total_skipped']}")
        
        if self.stats['unmapped_classes'] > 0:
            print(f"  ⚠️  Unmapped classes: {self.stats['unmapped_classes']}")
        if self.stats['skipped_boxes'] > 0:
            print(f"  ⚠️  Skipped bounding boxes: {self.stats['skipped_boxes']}")
        if self.stats['error_files'] > 0:
            print(f"  ❌ Error files: {self.stats['error_files']}")
        
        # Per-subdataset stats
        print("\n2️⃣  PHÂN BỐ THEO SUBDATASET:")
        for subdataset_name, stats in sorted(self.dataset_stats.items()):
            processed = stats['processed']
            skipped = stats['skipped']
            total = processed + skipped
            pct = (processed / total * 100) if total > 0 else 0
            print(f"  📦 {subdataset_name:25s}: {processed:5d} processed ({pct:5.1f}%)")
        
        # Class distribution
        print("\n3️⃣  PHÂN BỐ THEO CLASS (Bounding Boxes):")
        total_boxes = sum(self.class_stats.values())
        
        for class_id in sorted(self.class_stats.keys()):
            count = self.class_stats[class_id]
            class_name = self.label_mapping[class_id]['standard_name']
            percentage = (count / total_boxes * 100) if total_boxes > 0 else 0
            print(f"  [{class_id}] {class_name:20s}: {count:6d} boxes ({percentage:5.1f}%)")
        
        print(f"\n  📊 Tổng boxes: {total_boxes}")
        
        # Class imbalance warning
        if self.class_stats:
            counts = list(self.class_stats.values())
            max_count = max(counts)
            min_count = min(counts)
            imbalance_ratio = max_count / min_count if min_count > 0 else float('inf')
            
            print(f"\n⚖️  TỶ LỆ MẤT CÂN BẰNG: {imbalance_ratio:.2f}x")
            
            if imbalance_ratio > 10:
                print("  ❌ CẢNH BÁO: Dataset MẤT CÂN BẰNG NGHIÊM TRỌNG!")
                print("     Khuyến nghị:")
                print("     1. Sử dụng Copy-Paste augmentation (copy_paste=0.3)")
                print("     2. Tăng weight cho class ít mẫu")
                print("     3. Oversample class ít, undersample class nhiều")
            elif imbalance_ratio > 5:
                print("  ⚠️  Cảnh báo: Dataset có mất cân bằng vừa phải")
                print("     Khuyến nghị: Sử dụng augmentation phù hợp")
        
        # Remapping summary
        print("\n4️⃣  CLASS REMAPPING SUMMARY:")
        print(f"  ✓ Subdatasets processed: {len(self.subdataset_mappings)}")
        for subdataset_name, mapping in sorted(self.subdataset_mappings.items()):
            print(f"    • {subdataset_name}: {len(mapping)} classes mapped")
        
        print("=" * 70)
    
    def _print_header(self) -> None:
        """In header của script"""
        print("\n" + "🌾" * 35)
        print("FIELD DATASET PREPARATION FOR YOLOv8 (v2)")
        print("Class ID Remapping & Multi-Dataset Merging")
        print("🌾" * 35)
        
        print("\n📋 CẤU HÌNH:")
        print(f"  Input:  {self.raw_data_path}")
        print(f"  Output: {self.output_path}")
        print(f"  Remap:  {'Enabled' if self.remap_enabled else 'Disabled'}")
        print(f"  Classes: {len(self.label_mapping)}")
        
        # Print standard class mapping
        print("\n📝 STANDARD CLASS MAPPING (TARGET):")
        for class_id, info in sorted(self.label_mapping.items(), key=lambda x: int(x[0])):
            aliases = ', '.join(info['aliases'][:3])  # Show first 3 aliases
            if len(info['aliases']) > 3:
                aliases += ", ..."
            print(f"  [{class_id}] {info['standard_name']:20s} ← {aliases}")
        
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
            
            # Tìm subdatasets
            subdatasets = self._find_subdatasets()
            
            # Process từng subdataset
            for subdataset_path in subdatasets:
                self._process_subdataset(subdataset_path)
            
            # Create data.yaml
            self._create_data_yaml()
            
            # Print statistics
            self._print_statistics()
            
            # Success message
            print("\n" + "✅" * 35)
            print("FIELD DATASET PREPARATION COMPLETED!")
            print("✅" * 35)
            print(f"\n📁 Output: {self.output_path.absolute()}")
            print(f"📄 Config: data.yaml")
            print(f"📊 Total images: {self.stats['total_processed']}")
            print(f"📦 Total boxes: {sum(self.class_stats.values())}")
            print("\n💡 NEXT STEPS:")
            print("  1. Kiểm tra data.yaml")
            print("  2. Review class distribution")
            print("  3. Fine-tune model: python train_v2_finetune.py")
            print()
            
        except Exception as e:
            print("\n" + "=" * 70)
            print("❌ LỖI XẢY RA")
            print("=" * 70)
            print(f"Error: {str(e)}")
            print("\n🔍 DEBUG CHECKLIST:")
            print("  1. Kiểm tra config_v2.yaml format")
            print("  2. Verify RAW_DATA_FIELD structure")
            print("  3. Check subdataset data.yaml files")
            print("  4. Ensure images/labels directories exist")
            print("  5. Verify file permissions")
            raise


def main():
    """Entry point của script"""
    print("\n" + "🚀" * 35)
    print("Starting Field Dataset Preparation...")
    print("🚀" * 35 + "\n")
    
    preparator = FieldDatasetPreparator(config_path="config_v2.yaml")
    preparator.prepare()
    
    print("\n" + "🎉" * 35)
    print("Script completed successfully!")
    print("🎉" * 35 + "\n")


if __name__ == "__main__":
    main()