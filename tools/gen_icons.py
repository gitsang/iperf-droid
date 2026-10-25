# -*- coding: utf-8 -*-
"""
生成 iPerf-TV 的电视横幅图标(banner)与桌面图标(mipmap)。

设计要点：
  - 横向 banner: 320x180 基准(Android TV 规范)，左侧是"屏幕+吞吐量柱状图"，右侧是字标
  - 桌面图标: 圆角方形徽标 + 上升柱状图，配自适应图标(前景/背景分离)
  - 全部 4 倍超采样后缩放，边缘平滑无锯齿
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
RES = os.path.normpath(RES)

FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_REG = "C:/Windows/Fonts/segoeui.ttf"
FONT_MONO = "C:/Windows/Fonts/consola.ttf"

# 主题色（与 res/values/colors.xml 保持一致）
BG_TOP = (11, 15, 22)
BG_BOTTOM = (26, 34, 49)
ACCENT = (61, 123, 255)
ACCENT_HI = (34, 211, 238)
TEXT_PRI = (242, 245, 250)
TEXT_SEC = (152, 162, 179)

SS = 4  # 超采样倍数


def lerp(c1, c2, t):
    return tuple(int(round(a + (b - a) * t)) for a, b in zip(c1, c2))


def vgradient(size, top, bottom):
    """纵向渐变背景"""
    w, h = size
    img = Image.new("RGB", (1, h))
    px = img.load()
    for y in range(h):
        px[0, y] = lerp(top, bottom, y / max(1, h - 1))
    return img.resize((w, h), Image.BILINEAR)


def radial_glow(img, cx, cy, radius, color, strength=0.35):
    """在背景上叠加一团柔和光晕"""
    w, h = img.size
    layer = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=int(255 * strength))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.45))
    glow = Image.new("RGB", (w, h), color)
    return Image.composite(glow, img, layer.point(lambda v: v)) if False else \
        Image.blend(img, Image.composite(glow, img, layer), 1.0)


def rounded_rect(draw, box, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def draw_bars(draw, x, y, w, h, count=5, gap_ratio=0.28, base_color=ACCENT, top_color=ACCENT_HI):
    """画一组由低到高的吞吐量柱状图"""
    slot = w / count
    bw = slot * (1 - gap_ratio)
    for i in range(count):
        # 高度按二次曲线递增，视觉上更像真实速率爬升
        frac = (i + 1) / count
        bh = h * (0.22 + 0.78 * (frac ** 1.55))
        bx0 = x + i * slot + (slot - bw) / 2
        bx1 = bx0 + bw
        by1 = y + h
        by0 = y + h - bh
        color = lerp(base_color, top_color, i / max(1, count - 1))
        draw.rounded_rectangle([bx0, by0, bx1, by1], radius=max(1, int(bw * 0.28)), fill=color)


def draw_arrow(draw, x, y, size, color):
    """右上角向上的箭头"""
    w = size
    h = size * 0.85
    thick = max(2, int(size * 0.22))
    # 斜线
    draw.line([x, y + h, x + w, y], fill=color, width=thick)
    # 箭头头部
    head = size * 0.42
    draw.line([x + w - head, y, x + w, y], fill=color, width=thick)
    draw.line([x + w, y, x + w, y + head], fill=color, width=thick)


def fit_font(draw, text, path, target_w, target_size, min_size=8):
    """按可用宽度自动缩小字号，保证文字不溢出边界"""
    size = target_size
    while size > min_size:
        f = ImageFont.truetype(path, size)
        bbox = draw.textbbox((0, 0), text, font=f)
        if bbox[2] - bbox[0] <= target_w:
            return f
        size = int(size * 0.94)
    return ImageFont.truetype(path, min_size)


def make_banner(bw, bh):
    """横向电视 banner"""
    w, h = bw * SS, bh * SS
    img = vgradient((w, h), BG_TOP, BG_BOTTOM).convert("RGBA")
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    r = int(w * 0.34)
    gd.ellipse([int(w * 0.05) - r, int(h * 0.5) - r, int(w * 0.05) + r, int(h * 0.5) + r],
               fill=(61, 123, 255, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(r * 0.5))
    img = Image.alpha_composite(img, glow)

    d = ImageDraw.Draw(img)

    # --- 左侧：屏幕面板 ---
    pad = h * 0.14
    panel_w = w * 0.30
    panel_h = h * 0.56
    px0 = w * 0.045
    py0 = (h - panel_h) / 2
    px1 = px0 + panel_w
    py1 = py0 + panel_h
    radius = int(panel_h * 0.13)

    # 屏幕外发光
    halo = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hd.rounded_rectangle([px0, py0, px1, py1], radius=radius, outline=(61, 123, 255, 130),
                         width=max(2, int(h * 0.014)))
    halo = halo.filter(ImageFilter.GaussianBlur(h * 0.035))
    img = Image.alpha_composite(img, halo)
    d = ImageDraw.Draw(img)

    rounded_rect(d, [px0, py0, px1, py1], radius, fill=(9, 13, 20, 255),
                 outline=ACCENT, width=max(2, int(h * 0.012)))

    # 屏幕内的柱状图
    inner_pad = panel_w * 0.16
    bars_x = px0 + inner_pad
    bars_y = py0 + panel_h * 0.20
    bars_w = panel_w - inner_pad * 2
    bars_h = panel_h * 0.62
    draw_bars(d, bars_x, bars_y, bars_w, bars_h, count=5)

    # 屏幕右上角箭头
    draw_arrow(d, px1 - panel_w * 0.26, py0 + panel_h * 0.13, panel_w * 0.17, ACCENT_HI)

    # 底座
    foot_y = py1 + h * 0.035
    d.rounded_rectangle([px0 + panel_w * 0.30, py1 + h * 0.005, px1 - panel_w * 0.30, foot_y],
                        radius=max(1, int(h * 0.012)), fill=(70, 84, 105))
    d.rounded_rectangle([px0 + panel_w * 0.12, foot_y, px1 - panel_w * 0.12, foot_y + h * 0.022],
                        radius=max(1, int(h * 0.011)), fill=(70, 84, 105))

    # --- 右侧：字标 ---
    tx = px1 + w * 0.055
    right_margin = w * 0.05
    avail = w - tx - right_margin

    f_title = fit_font(d, "iPerf-TV", FONT_BOLD, avail, int(h * 0.245))
    f_tag = fit_font(d, "NETWORK THROUGHPUT TESTER", FONT_MONO, avail, int(h * 0.088))
    f_sub = fit_font(d, "iperf3  \u00b7  TCP / UDP  \u00b7  Live Log", FONT_REG, avail, int(h * 0.098))

    d.text((tx, h * 0.225), "iPerf-TV", font=f_title, fill=TEXT_PRI)
    d.text((tx + 2, h * 0.505), "NETWORK THROUGHPUT TESTER", font=f_tag, fill=ACCENT_HI)
    d.text((tx + 2, h * 0.655), "iperf3  \u00b7  TCP / UDP  \u00b7  Live Log", font=f_sub, fill=TEXT_SEC)

    # 字标下方的渐隐装饰线
    line_w = min(avail, w * 0.34)
    for i in range(int(line_w)):
        t = i / line_w
        c = lerp(ACCENT, BG_BOTTOM, t)
        d.line([tx + i, h * 0.795, tx + i, h * 0.795 + max(1, int(h * 0.012))], fill=c)

    return img.resize((bw, bh), Image.LANCZOS)


def make_icon(size, round_shape=False):
    """桌面图标(带背景的完整徽标)"""
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if round_shape:
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, s, s], fill=255)
    else:
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=255)

    grad = vgradient((s, s), (30, 42, 68), (15, 22, 38)).convert("RGBA")
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)

    # 顶部高光
    hl = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([-s * 0.3, -s * 0.55, s * 1.3, s * 0.45],
                               fill=(61, 123, 255, 80))
    hl = hl.filter(ImageFilter.GaussianBlur(s * 0.12))
    img = Image.alpha_composite(img, hl)
    d = ImageDraw.Draw(img)

    # 柱状图
    pad = s * 0.22
    draw_bars(d, pad, s * 0.34, s - pad * 2, s * 0.44, count=4)
    # 箭头
    draw_arrow(d, s * 0.52, s * 0.16, s * 0.30, ACCENT_HI)

    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def make_fg(size):
    """自适应图标前景(108dp 画布, 内容限制在中间 72dp 安全区)"""
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    safe = s * 72.0 / 108.0
    off = (s - safe) / 2
    draw_bars(d, off + safe * 0.14, off + safe * 0.34, safe * 0.72, safe * 0.44, count=4)
    draw_arrow(d, off + safe * 0.42, off + safe * 0.14, safe * 0.34, ACCENT_HI)
    return img.resize((size, size), Image.LANCZOS)


def make_bg(size):
    """自适应图标背景"""
    s = size * SS
    grad = vgradient((s, s), (30, 42, 68), (15, 22, 38)).convert("RGBA")
    hl = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([-s * 0.3, -s * 0.55, s * 1.3, s * 0.45], fill=(61, 123, 255, 90))
    hl = hl.filter(ImageFilter.GaussianBlur(s * 0.12))
    grad = Image.alpha_composite(grad, hl)
    return grad.resize((size, size), Image.LANCZOS)


def save(img, folder, name):
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, name + ".png")
    img.save(path, "PNG", optimize=True)
    print("  %-42s %s" % (folder + "/" + name + ".png", img.size))


def main():
    print("== banner ==")
    for folder, bw, bh in [
        ("drawable-mdpi", 160, 90),
        ("drawable-hdpi", 240, 135),
        ("drawable-xhdpi", 320, 180),
        ("drawable-xxhdpi", 480, 270),
        ("drawable-xxxhdpi", 640, 360),
    ]:
        save(make_banner(bw, bh), folder, "tv_banner")

    print("== launcher icon ==")
    for folder, px in [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192),
    ]:
        save(make_icon(px), folder, "ic_launcher")
        save(make_icon(px, round_shape=True), folder, "ic_launcher_round")

    print("== adaptive icon ==")
    for folder, px in [
        ("mipmap-mdpi", 108),
        ("mipmap-hdpi", 162),
        ("mipmap-xhdpi", 216),
        ("mipmap-xxhdpi", 324),
        ("mipmap-xxxhdpi", 432),
    ]:
        save(make_fg(px), folder, "ic_launcher_foreground")
        save(make_bg(px), folder, "ic_launcher_background")

    # 预览图(直接看效果)
    prev = os.path.join(os.path.dirname(RES), "..", "..", "..", "preview")
    prev = os.path.normpath(os.path.join(RES, "..", "..", "..", "preview"))
    os.makedirs(prev, exist_ok=True)
    make_banner(640, 360).save(os.path.join(prev, "banner_preview.png"))
    make_icon(192).save(os.path.join(prev, "icon_preview.png"))
    print("预览输出:", prev)


if __name__ == "__main__":
    main()
