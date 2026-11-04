#!/usr/bin/env python3
"""Editorial slides for Folio: the making of the plugin, then the demonstration.

English type is Times New Roman in the PowerPoint. The slide PDF embeds
Liberation Serif, the metric equivalent available on this machine, plus a
Chinese face for the interface labels.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.oxml.ns import qn
from pptx.util import Emu, Inches, Pt
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas as pdfcanvas
from reportlab.lib.colors import Color, HexColor, white

ROOT = Path("/workspace/submission/documents")
FIG = ROOT / "figures"
SHOTS = Path("/tmp/folio-walk/shots")

SERIF = "/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf"
SERIF_B = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
SERIF_I = "/usr/share/fonts/truetype/liberation/LiberationSerif-Italic.ttf"
CJK = "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"

pdfmetrics.registerFont(TTFont("Serif", SERIF))
pdfmetrics.registerFont(TTFont("Serif-B", SERIF_B))
pdfmetrics.registerFont(TTFont("Serif-I", SERIF_I))
pdfmetrics.registerFont(TTFont("CJK", CJK, subfontIndex=0))

INK = (0x1C, 0x19, 0x15)
PAPER = (0xF6, 0xF1, 0xE7)
BURG = (0x7A, 0x30, 0x28)
MUTED = (0x5E, 0x57, 0x4E)
CARD = (0xFF, 0xFC, 0xF7)
LINE = (0x1C, 0x19, 0x15)

C_INK = RGBColor(*INK)
C_PAPER = RGBColor(*PAPER)
C_BURG = RGBColor(*BURG)
C_MUTED = RGBColor(*MUTED)
C_CARD = RGBColor(*CARD)
C_WHITE = RGBColor(0xFF, 0xFF, 0xFF)

W_IN, H_IN = 13.333, 7.5
PAGE = (W_IN * 72, H_IN * 72)


def font(size: int, bold: bool = False):
    return ImageFont.truetype(SERIF_B if bold else SERIF, size)


def prepare_figures() -> None:
    FIG.mkdir(parents=True, exist_ok=True)
    box = (908, 122, 1864, 1128)
    names = {
        "files": "24-files.png",
        "citations": "01-citations.png",
        "excerpt": "02-source-excerpt.png",
        "drill": "10-explanation-open.png",
        "settings": "25-settings.png",
    }
    for name, src in names.items():
        im = Image.open(SHOTS / src).convert("RGB").crop(box)
        if name == "settings":
            im = im.crop((0, 0, im.width, 560))
        im.save(FIG / f"{name}.png")
    draw_process(FIG / "process.png")


def _card(draw: ImageDraw.ImageDraw, xy, number: str, title: str, body: str):
    x, y, w, h = xy
    draw.rounded_rectangle((x, y, x + w, y + h), radius=18, fill=CARD, outline=LINE, width=2)
    draw.text((x + 28, y + 22), number, font=font(28, True), fill=BURG)
    draw.text((x + 28, y + 68), title, font=font(32, True), fill=INK)
    # wrap body
    words = body.split()
    line, lines = "", []
    for word in words:
        trial = word if not line else line + " " + word
        if font(22).getlength(trial) < w - 56:
            line = trial
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    yy = y + 122
    for line in lines[:4]:
        draw.text((x + 28, yy), line, font=font(22), fill=MUTED)
        yy += 32


def draw_process(path: Path) -> None:
    im = Image.new("RGB", (2400, 640), PAPER)
    d = ImageDraw.Draw(im)
    steps = [
        ("01", "Restate", "The brief is given to the agent and asked back in its own words."),
        ("02", "Divide", "Points become modules. Decisions that are ours are asked."),
        ("03", "Draw", "Architect the system. Statecharts show the parts we must see."),
        ("04", "Revise", "We read the drawings and make the small changes."),
        ("05", "Prove", "A verify-skill is written for this plugin alone."),
        ("06", "Build", "The code follows the architecture. The skill is then run."),
        ("07", "Shape", "The interface changes from our notes, then is clicked through."),
        ("08", "Reply", "Cloud agents do the work. We answer from a phone."),
    ]
    w, h, gx, gy = 540, 250, 40, 28
    for i, (n, title, body) in enumerate(steps):
        col, row = i % 4, i // 4
        x = 40 + col * (w + gx)
        y = 40 + row * (h + gy)
        _card(d, (x, y, w, h), n, title, body)
        if col < 3:
            ax = x + w + 6
            ay = y + h // 2
            d.line((ax, ay, ax + gx - 14, ay), fill=BURG, width=3)
            d.polygon([(ax + gx - 14, ay - 8), (ax + gx - 2, ay), (ax + gx - 14, ay + 8)], fill=BURG)
    im.save(path)


def _flow(draw, boxes, y, box_w, box_h, gap, origin_x=40):
    x = origin_x
    for i, (title, sub) in enumerate(boxes):
        draw.rounded_rectangle((x, y, x + box_w, y + box_h), radius=16, fill=CARD, outline=LINE, width=2)
        draw.text((x + 22, y + 22), title, font=font(30, True), fill=INK)
        draw.text((x + 22, y + 70), sub, font=font(20), fill=MUTED)
        if i < len(boxes) - 1:
            ax = x + box_w + 4
            ay = y + box_h // 2
            draw.line((ax, ay, ax + gap - 16, ay), fill=BURG, width=3)
            draw.polygon([(ax + gap - 16, ay - 8), (ax + gap - 4, ay), (ax + gap - 16, ay + 8)], fill=BURG)
        x += box_w + gap


def draw_rag(path: Path) -> None:
    im = Image.new("RGB", (2400, 1100), PAPER)
    d = ImageDraw.Draw(im)
    d.text((48, 36), "Local retrieval. The model never sees a file it was not given.", font=font(28), fill=MUTED)
    _flow(d, [
        ("Files", "PDF, PPTX, text"),
        ("Parse", "Tika, by page"),
        ("Chunk", "700 / 100"),
        ("Index", "BM25, in process"),
    ], 140, 500, 150, 70)
    _flow(d, [
        ("Question", "plus the selection"),
        ("Retrieve", "at most four"),
        ("Stream", "answer, then thought"),
        ("Cite", "[n] maps to a page"),
    ], 470, 500, 150, 70)
    # Index feeds Retrieve, not Cite.
    d.line((2000, 290, 2000, 370), fill=BURG, width=3)
    d.line((2000, 370, 860, 370), fill=BURG, width=3)
    d.line((860, 370, 860, 466), fill=BURG, width=3)
    d.polygon([(852, 454), (860, 472), (868, 454)], fill=BURG)
    d.text((48, 760), "History is quoted for the model. It is not evidence for a citation.", font=font(28), fill=INK)
    d.text((48, 830), "A drill reads the same stream and shows three exercises only after they parse.", font=font(28), fill=INK)
    d.text((48, 920), "No embedding service. A missing key still retrieves, and makes no HTTP call.", font=font(28), fill=INK)
    im.save(path)


def draw_question(path: Path) -> None:
    im = Image.new("RGB", (980, 1400), PAPER)
    d = ImageDraw.Draw(im)
    rows = [
        ("1", "Ask", "The question and any selected code."),
        ("2", "Find", "BM25 returns at most four chunks."),
        ("3", "Write", "Answer text arrives as tokens. Thinking stays apart."),
        ("4", "Point", "After the reply, [1] becomes a file and a page."),
        ("5", "Open", "The source title shows the indexed excerpt."),
    ]
    y = 30
    for n, title, body in rows:
        d.ellipse((36, y, 100, y + 64), fill=BURG)
        d.text((54, y + 12), n, font=font(28, True), fill=(255, 252, 247))
        d.text((130, y + 4), title, font=font(32, True), fill=INK)
        d.text((130, y + 48), body, font=font(22), fill=MUTED)
        if n != "5":
            d.line((68, y + 68, 68, y + 118), fill=BURG, width=3)
        y += 150
    im.save(path)


def draw_actions(path: Path) -> None:
    im = Image.new("RGB", (1600, 980), PAPER)
    d = ImageDraw.Draw(im)
    cols = [
        ("Practice", "Three exercises.", "Each explanation opens alone.", "The editor is not touched."),
        ("Tests", "A scenario first.", "Then a Java preview.", "A file appears only on Write."),
        ("Commits", "Staged diff only.", "Candidates can be copied.", "Folio never commits."),
    ]
    for i, lines in enumerate(cols):
        x = 36 + i * 520
        d.rounded_rectangle((x, 40, x + 490, 920), radius=18, fill=CARD, outline=LINE, width=2)
        d.rectangle((x, 40, x + 490, 56), fill=BURG)
        yy = 90
        d.text((x + 28, yy), lines[0], font=font(36, True), fill=INK)
        yy = 190
        for line in lines[1:]:
            d.text((x + 28, yy), line, font=font(24), fill=MUTED)
            yy += 70
    im.save(path)


# --- PowerPoint -------------------------------------------------------------

def _run(paragraph, text, size, bold=False, color=C_INK, italic=False, align_note=None):
    run = paragraph.add_run()
    run.text = text
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = color
    run.font.name = "Times New Roman"
    rpr = run._r.get_or_add_rPr()
    for tag in ("a:latin", "a:ea", "a:cs"):
        node = rpr.find(qn(tag))
        if node is not None:
            rpr.remove(node)
    latin = rpr.makeelement(qn("a:latin"))
    latin.set("typeface", "Times New Roman")
    ea = rpr.makeelement(qn("a:ea"))
    ea.set("typeface", "SimSun")
    cs = rpr.makeelement(qn("a:cs"))
    cs.set("typeface", "Times New Roman")
    rpr.append(latin)
    rpr.append(ea)
    rpr.append(cs)
    return run


def _tb(slide, l, t, w, h, lines, align=PP_ALIGN.LEFT):
    box = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(h))
    frame = box.text_frame
    frame.word_wrap = True
    frame.auto_size = None
    frame.margin_left = Emu(0)
    frame.margin_right = Emu(0)
    frame.margin_top = Emu(0)
    frame.margin_bottom = Emu(0)
    for i, line in enumerate(lines):
        p = frame.paragraphs[0] if i == 0 else frame.add_paragraph()
        p.alignment = line.get("align", align)
        p.space_before = Pt(line.get("before", 0))
        p.space_after = Pt(line.get("after", 4))
        _run(p, line["text"], line.get("size", 18), line.get("bold", False), line.get("color", C_INK), line.get("italic", False))
    return box


def _rect(slide, l, t, w, h, fill):
    shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(l), Inches(t), Inches(w), Inches(h))
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    shape.line.fill.background()
    return shape


def _pic(slide, name, l, t, h, center=False):
    path = str(FIG / name)
    with Image.open(path) as im:
        aspect = im.width / im.height
    width_in = h * aspect
    if center:
        if width_in > 12.4:
            width_in = 12.4
            h = width_in / aspect
        l = (W_IN - width_in) / 2
    elif l + width_in > 12.9:
        h = (12.9 - l) / aspect
    height = Inches(h)
    width = Inches(h * aspect)
    frame = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(l) - Emu(8000), Inches(t) - Emu(8000), width + Emu(16000), height + Emu(16000))
    frame.fill.background()
    frame.line.color.rgb = C_INK
    frame.line.width = Pt(1)
    slide.shapes.add_picture(path, Inches(l), Inches(t), height=height)
    return h * aspect


def _chrome(slide, n, total):
    _rect(slide, 0, 7.18, W_IN, 0.32, C_INK)
    _tb(slide, 0.42, 7.22, 8, 0.24, [{"text": "Folio    ·    CST3492 Group B    ·    Team 4", "size": 11, "color": C_WHITE}])
    _tb(slide, 10.6, 7.22, 2.3, 0.24, [{"text": f"{n}   /   {total}", "size": 11, "color": C_WHITE, "align": PP_ALIGN.RIGHT}])


def _kicker(slide, text):
    _tb(slide, 0.48, 0.28, 12, 0.32, [{"text": text.upper(), "size": 13, "color": C_BURG, "bold": True}])


def _notes(slide, text):
    slide.notes_slide.notes_text_frame.text = text


def draw_making_board(path: Path) -> None:
    """The making sequence in the user's own order, laid out as two full rows."""
    width, height = 2600, 1500
    image = Image.new("RGB", (width, height), PAPER)
    draw = ImageDraw.Draw(image)
    fills = {
        "Send": (0xD7, 0xE4, 0xF2),
        "Restate": (0xD7, 0xE4, 0xF2),
        "Divide": (0xF3, 0xE0, 0xD2),
        "Ask": (0xF3, 0xE0, 0xD2),
        "Answer": (0xF3, 0xE0, 0xD2),
        "Draw": (0xE4, 0xDC, 0xF2),
        "Edit": (0xE4, 0xDC, 0xF2),
        "Skill": (0xD5, 0xE8, 0xE4),
        "Build": (0xD5, 0xE8, 0xE4),
        "Prove": (0xD5, 0xE8, 0xE4),
        "Notes": (0xEF, 0xE4, 0xD4),
        "Click": (0xF6, 0xDC, 0xE8),
    }
    top = [
        ("Send", "Send the brief", "“restate it…”"),
        ("Restate", "The agent", "restates it"),
        ("Divide", "Points and", "modules"),
        ("Ask", "The agent asks", "us to decide"),
        ("Answer", "We", "answer"),
        ("Draw", "/architect", "/statechart"),
    ]
    bottom = [
        ("Edit", "Small", "edits"),
        ("Skill", "Verify-skill", "for this project"),
        ("Build", "Code follows", "the architecture"),
        ("Prove", "Skill is run", "backend works"),
        ("Notes", "Our UI notes", "The agent changes it"),
        ("Click", "Computer use", "screenshots"),
    ]
    box_w, box_h, gap = 380, 400, 44
    top_w = len(top) * box_w + (len(top) - 1) * gap
    left = (width - top_w) // 2
    y1, y2 = 28, 820

    def rounded(x, y, phase, a, b):
        draw.rounded_rectangle((x, y, x + box_w, y + box_h), radius=28, fill=fills[phase], outline=LINE, width=4)
        draw.text((x + 28, y + 36), phase.upper(), font=font(26, True), fill=BURG)
        draw.text((x + 28, y + 140), a, font=font(42, True), fill=INK)
        draw.text((x + 28, y + 200), b, font=font(42, True), fill=INK)

    def shaft(x1, y1, x2, y2):
        draw.line((x1, y1, x2, y2), fill=BURG, width=6)

    def head(x, y, toward):
        size = 16
        if toward == "right":
            draw.polygon([(x, y), (x - size, y - 11), (x - size, y + 11)], fill=BURG)
        elif toward == "down":
            draw.polygon([(x, y), (x - 11, y - size), (x + 11, y - size)], fill=BURG)

    def caption(text, x, y):
        face = font(28, True)
        w = face.getlength(text)
        draw.text((x - w / 2, y), text, font=face, fill=BURG)

    for i, (phase, a, b) in enumerate(top):
        rounded(left + i * (box_w + gap), y1, phase, a, b)
    for i, (phase, a, b) in enumerate(bottom):
        rounded(left + i * (box_w + gap), y2, phase, a, b)

    for row_y, count in ((y1, len(top)), (y2, len(bottom))):
        for i in range(count - 1):
            x1 = left + i * (box_w + gap) + box_w
            mid_y = row_y + box_h / 2
            shaft(x1 + 4, mid_y, x1 + gap - 18, mid_y)
            head(x1 + gap - 2, mid_y, "right")

    last = left + 5 * (box_w + gap)
    drop_x = last + box_w / 2
    rail_y = (y1 + box_h + y2) // 2
    into_x = left + box_w / 2
    shaft(drop_x, y1 + box_h + 4, drop_x, rail_y)
    shaft(drop_x, rail_y, into_x, rail_y)
    shaft(into_x, rail_y, into_x, y2 - 20)
    head(into_x, y2 - 2, "down")
    caption("we review, then small edits", (drop_x + into_x) / 2, rail_y - 46)

    banner = "Cloud agents do every step. You read each result on a phone and reply."
    face = font(32, True)
    draw.text(((width - face.getlength(banner)) / 2, height - 70), banner, font=face, fill=BURG)
    image.save(path)


def build_pptx(path: Path) -> None:
    prepare_figures()
    draw_making_board(FIG / "making-flow.png")
    prs = Presentation()
    prs.slide_width = Inches(W_IN)
    prs.slide_height = Inches(H_IN)
    prs.core_properties.title = "Folio — CST3492 Group B, Team 4"
    prs.core_properties.author = "Luo Zhen, Li Canwen, Fan Yu, Meng Shengyu"
    prs.core_properties.subject = "How Folio was made, and what the demonstration shows"
    blank = prs.slide_layouts[6]
    total = 7

    def new():
        slide = prs.slides.add_slide(blank)
        _rect(slide, 0, 0, W_IN, H_IN, C_PAPER)
        _rect(slide, 0, 0, 0.12, H_IN, C_BURG)
        return slide

    # 1 name and members
    s = new()
    _chrome(s, 1, total)
    _tb(s, 0.7, 0.85, 11, 0.35, [{"text": "CST3492      ·      GROUP B      ·      TEAM 4", "size": 14, "bold": True, "color": C_BURG}])
    _tb(s, 0.68, 1.35, 12, 1.3, [{"text": "Folio", "size": 72, "bold": True}])
    _tb(s, 0.72, 2.85, 11, 1.1, [
        {"text": "A course assistant inside IntelliJ.", "size": 26, "italic": True},
        {"text": "It answers from the lectures, and it shows the page.", "size": 26, "italic": True},
    ])
    _rect(s, 0.72, 4.35, 2.2, 0.015, C_BURG)
    members = [
        ("2451989", "罗臻", "Luo Zhen"),
        ("2452270", "李灿文", "Li Canwen"),
        ("2452750", "范羽", "Fan Yu"),
        ("2452272", "孟圣雨", "Meng Shengyu"),
    ]
    for i, (sid, name, roman) in enumerate(members):
        x = 0.72 + (i % 2) * 5.6
        y = 4.6 + (i // 2) * 0.85
        _tb(s, x, y, 5.2, 0.75, [
            {"text": f"{name}    {roman}", "size": 22, "bold": True},
            {"text": sid, "size": 14, "color": C_MUTED, "before": 0},
        ])
    _notes(s, "Open here. Read the name, then the four people once. Say this is Group B, Team 4. About forty seconds. Do not open the IDE yet.")

    # 2 what it is, and what the brief asked, on one page
    s = new()
    _chrome(s, 2, total)
    _kicker(s, "02    Project")
    _tb(s, 0.48, 0.55, 6.4, 0.7, [{"text": "What it is", "size": 32, "bold": True}])
    _tb(s, 0.48, 1.35, 6.3, 2.3, [
        {"text": "A tool window on the right of IntelliJ. It answers from the lectures on this machine, and it names the page.", "size": 18, "after": 8},
        {"text": "The picture is that answer: @Test, from Lec-3.4, pages 3 and 2.", "size": 18, "italic": True},
    ])
    rows = [
        ("F0", "Course retrieval. Chunk, index, retrieve, cite the file and the page."),
        ("F1", "Three actions. A drill. A JUnit preview written only when asked. Commit text from the staged diff."),
        ("Not ours", "Group C rewrites the student’s code and drives the diff viewer."),
    ]
    for i, (k, line) in enumerate(rows):
        y = 3.9 + i * 0.95
        _tb(s, 0.48, y, 1.7, 0.7, [{"text": k, "size": 16, "bold": True, "color": C_BURG}])
        _tb(s, 2.2, y, 4.6, 0.85, [{"text": line, "size": 16}])
    _pic(s, "citations.png", 7.15, 0.7, 6.1)
    _notes(s, "One page for the product and the brief. Point at the screenshot, then read F0, F1, and the Group C line. About one minute.")

    # 3 making process, checked as a statechart
    s = new()
    _chrome(s, 3, total)
    _kicker(s, "03    Making")
    _tb(s, 0.42, 0.18, 8, 0.32, [{"text": "How it was made", "size": 22, "bold": True}])
    _pic(s, "making-flow.png", 0.28, 0.52, 6.5, center=True)
    _notes(s, "Read the top row left to right, then the bottom row. You send the brief and ask for a restatement. The agent extracts points, splits modules, and asks where a person must decide. Then /architect and /statechart. After your small edits, a verify-skill is written for this project. The code follows the architecture, and only then is the skill run. UI notes come after the backend is proven. The agent changes the window, then computer use clicks and sends screenshots. The line at the bottom is the whole process: cloud agents, and you reply from a phone. About two minutes.")

    # 5 decisions
    s = new()
    _chrome(s, 4, total)
    _kicker(s, "03    Making")
    _tb(s, 0.48, 0.62, 12, 0.8, [{"text": "Decisions that stayed with us", "size": 34, "bold": True}])
    _tb(s, 0.48, 1.55, 12.2, 1.0, [{"text": "After the restatement, the agent named the choices it would not make alone.", "size": 20, "italic": True}])
    decisions = [
        ("Retrieval", "A local word index, not an embedding service. The citation has to survive a dead network."),
        ("The third action", "A course drill. Rewriting code would repeat Group C. Answering again would repeat F0."),
        ("Tests", "Show a preview. Write the file only after an explicit click."),
        ("Commits", "Show the message. Do not run git commit."),
        ("The model", "The address stays in settings. This demonstration uses DeepSeek."),
    ]
    for i, (k, v) in enumerate(decisions):
        y = 2.45 + i * 0.85
        _tb(s, 0.5, y, 2.8, 0.7, [{"text": k, "size": 20, "bold": True, "color": C_BURG}])
        _tb(s, 3.4, y, 9.3, 0.75, [{"text": v, "size": 20}])
    _notes(s, "These are the answers we gave. One minute. If asked why not vectors: a Chinese question still has to hit an English slide by its words, and the page has to appear before the network returns.")

    s = new()
    _chrome(s, 5, total)
    _kicker(s, "04    The window")
    _tb(s, 0.48, 0.52, 12, 0.5, [{"text": "What the window does", "size": 32, "bold": True}])
    features = [
        ("Import", "PDF, PPTX, TXT, Markdown, and DOCX. Indexing needs no key. The library is shared. Remove asks first. The original file stays."),
        ("Ask", "The answer streams. Thinking stays apart. Stop keeps the text already shown."),
        ("Cite", "[1] names the file and the page. The title opens that page. A miss is a direct answer, with no invented page. No key still retrieves, and makes no model call."),
        ("Drill", "Three exercises appear together once they parse. Tokens are not painted on the way. The editor is not changed."),
        ("Tests", "A preview first. It is not run. Write file is the only write, and it can be undone."),
        ("Commit", "Stage is its own click. Then candidates. Copy or fill. The agent does not run git commit."),
        ("Settings", "Address and model live here. The key goes to the OS keyring. The field is empty once it is saved."),
        ("Drafts", "Ask, drill, test, and commit each keep their unsent text. A new chat clears the transcript. Drafts stay."),
    ]
    for i, (name, line) in enumerate(features):
        col, row = i % 2, i // 2
        x = 0.45 + col * 6.4
        y = 1.25 + row * 1.4
        _tb(s, x, y, 6.1, 1.25, [
            {"text": name, "size": 18, "bold": True, "color": C_BURG},
            {"text": line, "size": 15, "before": 2},
        ])
    _notes(s, "Read down the left column, then the right. This page is the inventory. Do not open the IDE. About ninety seconds.")

    s = new()
    _chrome(s, 6, total)
    _kicker(s, "04    The window")
    _tb(s, 0.48, 0.48, 12, 0.5, [{"text": "From a file to a page", "size": 32, "bold": True}])
    steps = [
        ("1  File", "PDF, PPTX, or text. Page, slide, and section numbers stay the ones in the file."),
        ("2  Chunk", "Each of those is split at about 700 characters, with 100 characters of overlap."),
        ("3  Index", "A local word index on this machine. No embedding service."),
        ("4  Retrieve", "The question and any selection. At most four chunks. A chunk must cover at least half the question’s terms."),
        ("5  Answer", "The model writes from those chunks and marks one as [1]."),
        ("6  Page", "[1] is the chunk that was used. The title opens that page."),
    ]
    for i, (name, line) in enumerate(steps):
        y = 1.15 + i * 0.9
        _tb(s, 0.45, y, 1.7, 0.75, [{"text": name, "size": 16, "bold": True, "color": C_BURG}])
        _tb(s, 2.2, y, 4.5, 0.8, [{"text": line, "size": 15}])
    _rect(s, 6.95, 1.2, 5.85, 5.3, C_CARD)
    _tb(s, 7.15, 1.4, 5.5, 4.9, [
        {"text": "A Chinese question", "size": 20, "bold": True, "after": 8},
        {"text": "「根据课件，JUnit 5 的测试方法通常用哪个注解？」", "size": 16, "after": 10},
        {"text": "The lectures are in English. The Chinese fragments are not on those pages. If those fragments also had to be covered, the question would match nothing.", "size": 15, "after": 8},
        {"text": "When none of the Chinese is in the library, and a Latin word such as JUnit is, the search uses the Latin word alone.", "size": 15, "after": 8},
        {"text": "That question hits Lec-3.4, pages 3 and 2.", "size": 15, "bold": True, "after": 10},
        {"text": "A Chinese question with no Latin word from the lectures does not take this path. One Latin word inside a long unrelated question is still not enough.", "size": 15},
    ])
    _notes(s, "Walk the six steps, then the card on the right. Say the Chinese rule in one breath: Chinese that the library does not contain is set aside only when a Latin word from the lectures is present. About two minutes.")

    s = new()
    _chrome(s, 7, total)
    _kicker(s, "04    The window")
    _tb(s, 0.48, 0.5, 12, 0.55, [{"text": "Two ways the project can change", "size": 32, "bold": True}])
    _tb(s, 0.5, 1.3, 6, 0.4, [{"text": "Tests", "size": 24, "bold": True, "color": C_BURG}])
    _tb(s, 0.5, 1.9, 6, 3.6, [
        {"text": "You describe the scenario.", "size": 18, "after": 8},
        {"text": "A Java preview comes back. It is checked for syntax and for looking like a test. It is not run.", "size": 18, "after": 8},
        {"text": "You click Write. The file appears, and it can be undone.", "size": 18, "after": 8},
        {"text": "Cancel, or a preview that is not a test, writes nothing.", "size": 18},
    ])
    _tb(s, 6.9, 1.3, 6, 0.4, [{"text": "Commit text", "size": 24, "bold": True, "color": C_BURG}])
    _tb(s, 6.9, 1.9, 5.9, 3.6, [
        {"text": "Stage is a separate click.", "size": 18, "after": 8},
        {"text": "Folio captures that staged diff.", "size": 18, "after": 8},
        {"text": "Three candidates. Copy one, or fill the commit box.", "size": 18, "after": 8},
        {"text": "If the staging area has changed, copy and fill are refused. Nothing here runs git commit.", "size": 18},
    ])
    _tb(s, 0.5, 6.15, 12, 0.7, [{"text": "Ask and drill have no path to a file or a commit.", "size": 20, "italic": True}])
    _notes(s, "Left column, then right column, then the bottom line. About ninety seconds. Then open the IDE.")

    # The live demonstration is not repeated here.
    prs.save(str(path))
    return



def build_slide_pdf(path: Path) -> None:
    """Seven slides. Liberation Serif stands in for Times New Roman."""
    c = pdfcanvas.Canvas(str(path), pagesize=PAGE)
    c.setTitle("Folio — CST3492 Group B, Team 4")
    c.setAuthor("Luo Zhen, Li Canwen, Fan Yu, Meng Shengyu")
    pw, ph = PAGE
    total = 7

    def paper():
        c.setFillColor(HexColor("#F6F1E7"))
        c.rect(0, 0, pw, ph, fill=1, stroke=0)
        c.setFillColor(HexColor("#7A3028"))
        c.rect(0, 0, 8, ph, fill=1, stroke=0)
        c.setFillColor(HexColor("#1C1915"))
        c.rect(0, 0, pw, 22, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont("Serif", 9)
        c.drawString(28, 7, "Folio     ·     CST3492 Group B     ·     Team 4")

    def num(n):
        c.setFillColor(white)
        c.setFont("Serif", 9)
        c.drawRightString(pw - 24, 7, f"{n}   /   {total}")

    def mixed(x, y, text, size=16, face="Serif", color=HexColor("#1C1915")):
        c.setFillColor(color)
        cursor = x
        buf, mode = "", None

        def flush(kind, chunk):
            nonlocal cursor
            if not chunk:
                return
            use = "CJK" if kind == "cjk" else face
            c.setFont(use, size)
            c.drawString(cursor, y, chunk)
            cursor += c.stringWidth(chunk, use, size)

        for ch in text:
            kind = "cjk" if ord(ch) > 127 else "lat"
            if mode is None:
                mode = kind
            if kind != mode:
                flush(mode, buf)
                buf, mode = ch, kind
            else:
                buf += ch
        flush(mode, buf)

    def image(name, x, y, h, max_w=900):
        path_img = str(FIG / name)
        with Image.open(path_img) as im:
            aspect = im.width / im.height
        w = h * aspect
        if w > max_w:
            w = max_w
            h = w / aspect
        c.setStrokeColor(HexColor("#1C1915"))
        c.setLineWidth(0.6)
        c.rect(x - 1, y - 1, w + 2, h + 2, fill=0, stroke=1)
        c.drawImage(path_img, x, y, w, h, preserveAspectRatio=True, mask="auto")

    def kicker(text):
        c.setFillColor(HexColor("#7A3028"))
        c.setFont("Serif-B", 11)
        c.drawString(36, 500, text)

    def title(text, y=458):
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif-B", 26)
        c.drawString(36, y, text)

    # 1
    paper(); num(1)
    kicker("CST3492      ·      GROUP B      ·      TEAM 4")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 54)
    c.drawString(36, 400, "Folio")
    c.setFont("Serif-I", 16)
    c.drawString(40, 360, "A course assistant inside IntelliJ.")
    c.drawString(40, 338, "It answers from the lectures, and it shows the page.")
    members = [
        ("2451989", "罗臻  Luo Zhen"),
        ("2452270", "李灿文  Li Canwen"),
        ("2452750", "范羽  Fan Yu"),
        ("2452272", "孟圣雨  Meng Shengyu"),
    ]
    for i, (sid, name) in enumerate(members):
        x = 40 + (i % 2) * 420
        y = 250 - (i // 2) * 56
        mixed(x, y, name, 16, "Serif-B")
        c.setFillColor(HexColor("#5E574E"))
        c.setFont("Serif", 12)
        c.drawString(x, y - 18, sid)
    c.showPage()

    # 2
    paper(); num(2)
    kicker("02     PROJECT")
    title("What it is")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif", 13)
    lines = [
        "A tool window on the right of IntelliJ.",
        "It answers from the lectures on this machine,",
        "and it names the page.",
        "",
        "The picture is that answer: @Test,",
        "from Lec-3.4, pages 3 and 2.",
    ]
    y = 410
    for line in lines:
        c.drawString(36, y, line)
        y -= 20
    bits = [
        ("F0", "Course retrieval. Chunk, index, retrieve, cite the page."),
        ("F1", "A drill. A JUnit preview written only when asked."),
        ("", "Commit text from the staged diff."),
        ("Not ours", "Group C rewrites code and drives the diff viewer."),
    ]
    y = 250
    for k, line in bits:
        c.setFillColor(HexColor("#7A3028"))
        c.setFont("Serif-B", 12)
        c.drawString(36, y, k)
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif", 12)
        c.drawString(120, y, line)
        y -= 28
    image("citations.png", 500, 120, 340, max_w=430)
    c.showPage()

    # 3
    paper(); num(3)
    kicker("03     MAKING")
    title("How it was made", 512)
    image("making-flow.png", 28, 48, 430, max_w=900)
    c.showPage()

    # 4
    paper(); num(4)
    kicker("03     MAKING")
    title("Decisions that stayed with us")
    c.setFont("Serif-I", 13)
    c.setFillColor(HexColor("#1C1915"))
    c.drawString(36, 420, "After the restatement, the agent named the choices it would not make alone.")
    decisions = [
        ("Retrieval", "A local word index, not an embedding service."),
        ("The third action", "A course drill. Not Group C, and not another ask."),
        ("Tests", "Show a preview. Write the file only after a click."),
        ("Commits", "Show the message. Do not run git commit."),
        ("The model", "The address stays in settings. This demo uses DeepSeek."),
    ]
    y = 360
    for k, v in decisions:
        c.setFillColor(HexColor("#7A3028"))
        c.setFont("Serif-B", 14)
        c.drawString(36, y, k)
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif", 14)
        c.drawString(220, y, v)
        y -= 46
    c.showPage()

    # 5 features
    paper(); num(5)
    kicker("04     THE WINDOW")
    title("What the window does", 520)
    features = [
        (36, "Import", "PDF, PPTX, TXT, Markdown, DOCX. No key to index."),
        (36, "Ask", "The answer streams. Thinking stays apart."),
        (36, "Cite", "[1] is the file and the page. A miss invents no page."),
        (36, "Drill", "Three exercises after they parse. The editor stays."),
        (500, "Tests", "Preview first. Write file is the only write."),
        (500, "Commit", "Stage, then candidates. No git commit."),
        (500, "Settings", "The key is in the OS keyring. The field stays empty."),
        (500, "Drafts", "Each mode keeps its text. A new chat keeps drafts."),
    ]
    y_left, y_right = 430, 430
    for x, name, line in features:
        y = y_left if x == 36 else y_right
        c.setFillColor(HexColor("#7A3028"))
        c.setFont("Serif-B", 14)
        c.drawString(x, y, name)
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif", 13)
        c.drawString(x + 110, y, line)
        if x == 36:
            y_left -= 70
        else:
            y_right -= 70
    c.showPage()

    # 6 mechanism and Chinese
    paper(); num(6)
    kicker("04     THE WINDOW")
    title("From a file to a page", 512)
    steps = [
        "1  File. Page and slide numbers stay as they are.",
        "2  Chunk. About 700 characters, overlap 100.",
        "3  Index. Local words. No embedding service.",
        "4  Retrieve. At most four chunks. Half the terms.",
        "5  Answer. The model marks a chunk as [1].",
        "6  Page. [1] opens that file and page.",
    ]
    y = 450
    for line in steps:
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif", 14)
        c.drawString(36, y, line)
        y -= 36
    c.setStrokeColor(HexColor("#1C1915"))
    c.setLineWidth(1)
    c.roundRect(500, 70, 430, 400, 8, fill=0, stroke=1)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 14)
    c.drawString(518, 440, "A Chinese question")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("CJK", 12)
    c.drawString(518, 410, "根据课件，JUnit 5 的测试方法")
    c.drawString(518, 392, "通常用哪个注解？")
    c.setFont("Serif", 12)
    lines = [
        "The lectures are in English.",
        "Those Chinese fragments are not",
        "on the pages. If they also had to",
        "be covered, nothing would match.",
        "When none of the Chinese is in the",
        "library, and JUnit is, the search",
        "uses JUnit alone.",
        "It hits Lec-3.4, pages 3 and 2.",
        "No Latin word: this path is not used.",
        "One Latin word in a long unrelated",
        "question is still not enough.",
    ]
    y = 360
    for line in lines:
        c.drawString(518, y, line)
        y -= 22
    c.showPage()

    # 7 two writes
    paper(); num(7)
    kicker("04     THE WINDOW")
    title("Two ways the project can change", 520)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 18)
    c.drawString(36, 440, "Tests")
    c.drawString(500, 440, "Commit text")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif", 14)
    left = [
        "You describe the scenario.",
        "A Java preview. Syntax and shape.",
        "It is not run.",
        "You click Write. The file appears.",
        "Cancel writes nothing.",
    ]
    right = [
        "Stage is a separate click.",
        "Folio captures that staged diff.",
        "Three candidates.",
        "Copy one, or fill the commit box.",
        "A changed staging area is refused.",
        "Nothing here runs git commit.",
    ]
    y = 390
    for line in left:
        c.drawString(36, y, line)
        y -= 32
    y = 390
    for line in right:
        c.drawString(500, y, line)
        y -= 32
    c.setFont("Serif-I", 14)
    c.drawString(36, 80, "Ask and drill have no path to a file or a commit.")
    c.showPage()

    c.save()


if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    build_pptx(ROOT / "Folio-GroupB-Team4-Slides.pptx")
    build_slide_pdf(ROOT / "Folio-GroupB-Team4-Slides.pdf")
    print("slides written")
