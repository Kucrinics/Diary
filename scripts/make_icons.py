#!/usr/bin/env python3
"""Генерация иконок PWA: бирюзовый градиент + белая карточка расписания с цветными точками."""
from PIL import Image, ImageDraw

SIZES = {"icon-512.png": 512, "icon-192.png": 192, "apple-touch-icon.png": 180}


def rounded_gradient(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.new("RGBA", (size, size))
    top = (13, 148, 136)     # teal-600
    bottom = (15, 87, 84)    # тёмный teal
    for y in range(size):
        t = y / (size - 1)
        r = int(top[0] * (1 - t) + bottom[0] * t)
        g = int(top[1] * (1 - t) + bottom[1] * t)
        b = int(top[2] * (1 - t) + bottom[2] * t)
        for x in range(size):
            grad.putpixel((x, y), (r, g, b, 255))
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
    img.paste(grad, (0, 0), mask)
    return img


def draw_card(img: Image.Image, s: int) -> None:
    d = ImageDraw.Draw(img)
    m = int(s * 0.17)                       # отступ карточки
    card = [m, int(s * 0.24), s - m, s - m]
    d.rounded_rectangle(card, radius=int(s * 0.09), fill=(250, 253, 252, 255))
    # верхняя «шапка» карточки
    d.rounded_rectangle(
        [card[0], card[1], card[2], card[1] + int(s * 0.10)],
        radius=int(s * 0.09), fill=(15, 118, 110, 255),
    )
    d.rectangle([card[0], card[1] + int(s * 0.05), card[2], card[1] + int(s * 0.10)], fill=(15, 118, 110, 255))
    # строки: цветная точка + полоска
    rows = [(236, 72, 153), (245, 158, 11), (20, 184, 166), (139, 92, 246)]  # розовый, янтарь, бирюза, сирень
    y0 = card[1] + int(s * 0.17)
    step = int((card[3] - y0 - int(s * 0.06)) / len(rows))
    for i, color in enumerate(rows):
        cy = y0 + i * step + step // 2
        r = int(s * 0.032)
        cx = card[0] + int(s * 0.085)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (255,))
        ly0, ly1 = cy - int(s * 0.016), cy + int(s * 0.016)
        d.rounded_rectangle(
            [cx + int(s * 0.055), ly0, card[2] - int(s * 0.075), ly1],
            radius=int(s * 0.016), fill=(203, 213, 210, 255),
        )


for name, size in SIZES.items():
    img = rounded_gradient(size)
    draw_card(img, size)
    img.save(f"/home/z/my-project/public/{name}")
    print("ok", name)
