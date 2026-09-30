import pathlib, subprocess, sys, imageio_ffmpeg
from playwright.sync_api import sync_playwright

FPS = 30
here = pathlib.Path(__file__).parent
ff = imageio_ffmpeg.get_ffmpeg_exe()
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={'width': 600, 'height': 1150})
    pg.goto((here / 'index.html').as_uri())
    pg.wait_for_function("window.gsap && gsap.globalTimeline.getChildren(false,false,true).length")
    pg.evaluate("document.fonts.ready")
    pg.add_style_tag(content=".stage{border-radius:0!important}")
    dur = pg.evaluate("(window.T = gsap.globalTimeline.getChildren(false,false,true)[0]).pause(), T.duration()")
    stage = pg.locator('#stage')
    box = stage.bounding_box(); print(box, dur)
    enc = subprocess.Popen([ff, '-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', str(FPS), '-i', '-',
        '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', '-preset', 'slow', '-movflags', '+faststart', 'promo.mp4'],
        stdin=subprocess.PIPE)
    n = int(dur * FPS)
    for i in range(n):
        pg.evaluate("t => new Promise(r => { T.seek(t); requestAnimationFrame(() => requestAnimationFrame(r)); })", i / FPS)
        enc.stdin.write(stage.screenshot(type='png'))
        if i % 300 == 0: print(i, '/', n, flush=True)
    enc.stdin.close(); enc.wait(); b.close()
# README preview: GIF can't be both smooth and small, so 12fps/320px
subprocess.run([ff, '-y', '-loglevel', 'error', '-i', 'promo.mp4', '-vf',
    'fps=12,scale=320:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=4',
    'promo.gif'], check=True)
for f in ('promo.mp4', 'promo.gif'): print(f, (here / f).stat().st_size // 1024, 'KB')
