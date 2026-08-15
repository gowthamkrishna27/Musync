from PIL import Image, ImageDraw
import os

source_path = r"C:\Users\gowth\Downloads\ChatGPT Image Aug 15, 2026, 08_55_56 PM.png"
res_dir = r"c:\Users\gowth\Downloads\Musync\app\src\main\res"

img = Image.open(source_path).convert("RGBA")

# Adaptive Foreground Sizes
adaptive_sizes = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432
}

# Legacy Launcher Icon Sizes
legacy_sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

# 1. Full image as it is for adaptive foreground
for density, size in adaptive_sizes.items():
    density_folder = os.path.join(res_dir, density)
    os.makedirs(density_folder, exist_ok=True)
    
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    out_path = os.path.join(density_folder, "ic_launcher_foreground.png")
    resized.save(out_path, "PNG")
    print(f"Generated as-is {out_path} ({size}x{size})")

drawable_folder = os.path.join(res_dir, "drawable")
os.makedirs(drawable_folder, exist_ok=True)
img.resize((432, 432), Image.Resampling.LANCZOS).save(os.path.join(drawable_folder, "ic_launcher_foreground.png"), "PNG")

# 2. Full image as it is for legacy square and round icons
for density, size in legacy_sizes.items():
    density_folder = os.path.join(res_dir, density)
    
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    out_path = os.path.join(density_folder, "ic_launcher.png")
    resized.save(out_path, "PNG")
    
    # Round icon mask
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    
    round_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    round_canvas.paste(resized, (0, 0), mask)
    
    out_round_path = os.path.join(density_folder, "ic_launcher_round.png")
    round_canvas.save(out_round_path, "PNG")
    print(f"Generated as-is {out_path} and {out_round_path} ({size}x{size})")

print("All new icon assets generated successfully!")
