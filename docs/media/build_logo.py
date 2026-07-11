#!/usr/bin/env python3
"""Generates the augur logo set: a monochrome augural seal.

The mark is an intaglio-style medallion — a keyline coin edge with a pearled
bead border, a faint templum crosshair (the augur's marked quadrant of sky),
and the lituus (the augur's staff) rendered as a calligraphic device.
Single ink, no gradients. Run from the repo root:

    python3 docs/media/build_logo.py
"""
import math

CENTER = 60.0


def qsample(p0, c, p1, n):
    out = []
    for i in range(n):
        t = i / (n - 1)
        out.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0],
                    (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]))
    return out


def lituus_path():
    """A calligraphic lituus: broad shaft tapering into an open volute."""
    segs = [((57.5, 101), (60, 82), (63.5, 63)), ((63.5, 63), (66.5, 53), (60, 47.5)),
            ((60, 47.5), (52.5, 49), (52.5, 56)), ((52.5, 56), (53, 61), (57.5, 59.5))]
    # centre + enlarge so the staff commands the coin
    cx, cy, s, dy = 60, 74, 1.12, -12

    def tf(p):
        return (cx + (p[0] - cx) * s, cy + (p[1] - cy) * s + dy)
    spine = []
    for a, b, c in segs:
        pts = qsample(tf(a), tf(b), tf(c), 26)
        spine += pts if not spine else pts[1:]
    n = len(spine)

    def width(f):
        return 1.25 + 4.2 * (1 - f) ** 1.35

    def normal(i):
        a = spine[max(0, i - 1)]
        b = spine[min(n - 1, i + 1)]
        dx, dyy = b[0] - a[0], b[1] - a[1]
        ln = math.hypot(dx, dyy) or 1
        return (-dyy / ln, dx / ln)
    left, right = [], []
    for i, p in enumerate(spine):
        f = i / (n - 1)
        nx, ny = normal(i)
        w = width(f) / 2
        left.append((p[0] + nx * w, p[1] + ny * w))
        right.append((p[0] - nx * w, p[1] - ny * w))
    outline = right + [spine[-1]] + list(reversed(left))
    return "M" + " L".join("%.2f,%.2f" % (x, y) for x, y in outline) + " Z"


def beads(r, n, rad):
    return "\n    ".join(
        '<circle cx="%.2f" cy="%.2f" r="%s"/>' % (
            CENTER + r * math.cos(2 * math.pi * i / n),
            CENTER + r * math.sin(2 * math.pi * i / n), rad)
        for i in range(n))


LITUUS = lituus_path()


def seal(ink, cross_op, compact=False):
    if compact:
        return '''<circle cx="60" cy="60" r="54.5" fill="none" stroke="%s" stroke-width="3.4"/>
  <path d="%s" fill="%s"/>''' % (ink, LITUUS, ink)
    return '''<circle cx="60" cy="60" r="55" fill="none" stroke="%s" stroke-width="2.3"/>
  <circle cx="60" cy="60" r="49.5" fill="none" stroke="%s" stroke-width="0.9"/>
  <g fill="%s">
    %s
  </g>
  <g stroke="%s" stroke-width="0.7" opacity="%s">
    <line x1="60" y1="15" x2="60" y2="105"/><line x1="15" y1="60" x2="105" y2="60"/>
  </g>
  <path d="%s" fill="%s"/>''' % (
        ink, ink, ink, beads(52.5, 54, 0.72), ink, cross_op, LITUUS, ink)


FONT = ("'Trajan Pro', 'Cormorant Garamond', 'Optima', "
        "'Times New Roman', Georgia, serif")


def mark(ink, cross_op, compact=False):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" '
            'width="120" height="120" role="img" aria-label="augur">\n'
            '  <title>augur</title>\n  %s\n</svg>\n' % seal(ink, cross_op, compact))


def lockup(ink, cross_op):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 424 128" '
            'width="424" height="128" role="img" aria-label="augur">\n'
            '  <title>augur</title>\n'
            '  <g transform="translate(4,4) scale(0.96)">\n  %s\n  </g>\n'
            '  <text x="150" y="83" font-family="%s" font-size="60" '
            'letter-spacing="9" fill="%s">AUGUR</text>\n</svg>\n'
            % (seal(ink, cross_op), FONT, ink))


INK = "#1C1A15"       # warm near-black, one ink
PARCH = "#E7E0CF"     # warm parchment, for dark backgrounds

files = {
    "docs/media/logo.svg": lockup(INK, "0.22"),
    "docs/media/logo-dark.svg": lockup(PARCH, "0.30"),
    "docs/media/logo-mark.svg": mark(INK, "0.22"),
    "docs/media/logo-icon.svg": mark(INK, "0.22", compact=True),
}
for path, content in files.items():
    open(path, "w").write(content)
    print("wrote", path)
