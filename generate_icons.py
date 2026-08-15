from PIL import Image, ImageDraw, ImageOps
import os

source_path = r"C:\Users\gowth\Downloads\ChatGPT Image Aug 15, 2026, 04_25_00 PM.png"
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

# 1. Full uncropped image fitted onto adaptive foreground canvas
for density, size in adaptive_sizes.items():
    density_folder = os.path.join(res_dir, density)
    os.makedirs(density_folder, exist_ok=True)
    
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fitted = ImageOps.contain(img, (size, size), Image.Resampling.LANCZOS)
    
    offset_x = (size - fitted.width) // 2
    offset_y = (size - fitted.height) // 2
    canvas.paste(fitted, (offset_x, offset_y), fitted)
    
    out_path = os.path.join(density_folder, "ic_launcher_foreground.png")
    canvas.save(out_path, "PNG")
    print(f"Generated uncropped {out_path} ({size}x{size})")

drawable_folder = os.path.join(res_dir, "drawable")
os.makedirs(drawable_folder, exist_ok=True)
fitted_432 = ImageOps.contain(img, (432, 432), Image.Resampling.LANCZOS)
canvas_432 = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
canvas_432.paste(fitted_432, ((432 - fitted_432.width) // 2, (432 - fitted_432.height) // 2), fitted_432)
canvas_432.save(os.path.join(drawable_folder, "ic_launcher_foreground.png"), "PNG")

# 2. Full uncropped image fitted onto dark background (#0E0F14) for legacy launcher icons
for density, size in legacy_sizes.items():
    density_folder = os.path.join(res_dir, density)
    
    canvas = Image.new("RGBA", (size, size), (14, 15, 20, 255))
    fitted = ImageOps.contain(img, (size, size), Image.Resampling.LANCZOS)
    offset_x = (size - fitted.width) // 2
    offset_y = (size - fitted.height) // 2
    canvas.paste(fitted, (offset_x, offset_y), fitted)
    
    out_path = os.path.join(density_folder, "ic_launcher.png")
    canvas.save(out_path, "PNG")
    
    # Round icon mask
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    
    round_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    round_canvas.paste(canvas, (0, 0), mask)
    
    out_round_path = os.path.join(density_folder, "ic_launcher_round.png")
    round_canvas.save(out_round_path, "PNG")
    print(f"Generated uncropped {out_path} and {out_round_path} ({size}x{size})")

print("All uncropped icon assets generated successfully!")
