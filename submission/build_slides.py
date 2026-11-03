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
        ("01", "Restate", "The brief is given to Cursor and asked back in its own words."),
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


def build_pptx(path: Path) -> None:
    prepare_figures()
    prs = Presentation()
    prs.slide_width = Inches(W_IN)
    prs.slide_height = Inches(H_IN)
    prs.core_properties.title = "Folio — CST3492 Group B, Team 4"
    prs.core_properties.author = "Luo Zhen, Li Canwen, Fan Yu, Meng Shengyu"
    prs.core_properties.subject = "How Folio was made, and what the demonstration shows"
    blank = prs.slide_layouts[6]
    total = 17

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
        ("2452279", "李灿文", "Li Canwen"),
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

    # 2 introduction
    s = new()
    _chrome(s, 2, total)
    _kicker(s, "02    Project")
    _tb(s, 0.48, 0.62, 6.3, 1.0, [{"text": "What it is", "size": 36, "bold": True}])
    _tb(s, 0.48, 1.8, 6.2, 4.6, [
        {"text": "Folio lives in a tool window on the right of IntelliJ.", "size": 20, "after": 12},
        {"text": "It indexes the course files on the machine. A question retrieves a few pages and asks the model to answer from those pages.", "size": 20, "after": 12},
        {"text": "The reply names the file and the page. The page can be opened.", "size": 20, "after": 12},
        {"text": "Three further actions sit in the same window: practice, a JUnit preview, and commit messages.", "size": 20, "after": 12},
        {"text": "Group C owns rewriting source and the diff viewer. Folio does not.", "size": 20, "italic": True, "color": C_MUTED},
    ])
    _pic(s, "citations.png", 7.15, 0.85, 5.9)
    _notes(s, "Point at the screenshot: a Chinese question, the answer @Test, and two citations into Lec-3.4. That is the whole product in one picture. One minute.")

    # 3 brief
    s = new()
    _chrome(s, 3, total)
    _kicker(s, "02    Project")
    _tb(s, 0.48, 0.62, 12, 0.7, [{"text": "What the brief required", "size": 36, "bold": True}])
    items = [
        ("F0", "Course retrieval", "Cut the lectures into chunks. Index them. Retrieve the best. Cite the file and the page or the slide."),
        ("F1", "Three IDE actions", "A drill on the selection. A JUnit preview that is written only when asked. Commit messages from the staged diff."),
        ("Not ours", "Group C", "Changing the student’s code from an instruction, and driving the built-in diff viewer."),
    ]
    for i, (k, title, body) in enumerate(items):
        y = 1.6 + i * 1.7
        _rect(s, 0.5, y, 0.08, 1.4, C_BURG)
        _tb(s, 0.85, y, 11.5, 1.45, [
            {"text": k.upper(), "size": 13, "bold": True, "color": C_BURG},
            {"text": title, "size": 26, "bold": True, "before": 2},
            {"text": body, "size": 18, "color": C_MUTED, "before": 2},
        ])
    _notes(s, "Forty seconds. The rest of the talk is how this list became the window on the previous slide.")

    # 4 making process
    s = new()
    _chrome(s, 4, total)
    _kicker(s, "03    Making")
    _tb(s, 0.48, 0.58, 12, 0.6, [{"text": "How it was made", "size": 34, "bold": True}])
    _pic(s, "process.png", 0.4, 1.4, 5.45)
    _notes(s, "Walk the eight boxes left to right, top then bottom. Stress two things: we were asked before a human decision, and every result came back to a phone. About ninety seconds. Do not read every word in the boxes.")

    # 5 decisions
    s = new()
    _chrome(s, 5, total)
    _kicker(s, "03    Making")
    _tb(s, 0.48, 0.62, 12, 0.8, [{"text": "Decisions that stayed with us", "size": 34, "bold": True}])
    _tb(s, 0.48, 1.55, 12.2, 1.0, [{"text": "After the restatement, Cursor named the choices it would not make alone.", "size": 20, "italic": True}])
    decisions = [
        ("Retrieval", "A local word index, not an embedding service. The citation has to survive a dead network."),
        ("The third action", "A course drill. Rewriting code would repeat Group C. Answering again would repeat F0."),
        ("Tests", "Show a preview. Write the file only after an explicit click."),
        ("Commits", "Show the message. Do not run git commit, and do not touch the index."),
        ("The model", "The address stays in settings. This demonstration uses DeepSeek."),
    ]
    for i, (k, v) in enumerate(decisions):
        y = 2.45 + i * 0.85
        _tb(s, 0.5, y, 2.8, 0.7, [{"text": k, "size": 20, "bold": True, "color": C_BURG}])
        _tb(s, 3.4, y, 9.3, 0.75, [{"text": v, "size": 20}])
    _notes(s, "These are the answers we gave. One minute. If asked why not vectors: a Chinese question still has to hit an English slide by its words, and the page has to appear before the network returns.")

    def chart(n, title, image, caption, note):
        slide = new()
        _chrome(slide, n, total)
        _kicker(slide, "03    Making")
        _tb(slide, 0.48, 0.5, 12.2, 0.42, [{"text": title, "size": 26, "bold": True}])
        _tb(slide, 0.48, 0.98, 12.2, 0.32, [{"text": caption, "size": 13, "italic": True, "color": C_MUTED}])
        _pic(slide, image, 0.35, 1.38, 5.55, center=True)
        _notes(slide, note)

    chart(6, "Ownership, from the repository", "ownership.png",
          "docs/images/components.svg. Who owns the library, the key, and a write.",
          "This is the architecture drawing already in the repository. Top row is the IDE. Middle row is a project’s tasks and conversation. Bottom row is the shared library, the key, and the model or Git boundary. Workers do not read Swing fields. Forty-five seconds.")
    chart(7, "Tool window, checked with TLC", "state-window.png",
          "docs/statechart. One activity at a time. A key is required before Ask, Preview, or Draft.",
          "This is the statechart already in the repository. Setup, indexing, then Ask, Test, and Commit. Write is only from a preview that parsed as a test. Say the overlapping labels are the real chart, not a simplification. One minute.")
    chart(8, "Retrieval, checked with TLC", "state-retrieval.png",
          "A page citation exists only after a hit. A miss ends as general knowledge. Stop does not invent a page.",
          "This submodule was drawn with /statechart. TLC found 14 states. CiteOnlyWhenHit, MissNeverCited, and StoppedHasNoCitation all hold. Point at the two finishes: nothing retrieved, or a chunk was retrieved. One minute.")
    chart(9, "Test write, checked with TLC", "state-test.png",
          "An empty cancel writes nothing. Write is reachable only from a Java preview.",
          "Second submodule from /statechart. Six states. The file is a copy of a preview that parsed. Not a test has no arrow to Test file written. Twenty seconds.")
    chart(10, "Commit delivery, checked with TLC", "state-commit.png",
          "Copy and fill require the captured staging. Nothing in the model creates a commit.",
          "Third submodule from /statechart. Nine states. NeverCommits holds because no action sets a commit. After the index changes, copy and fill are refused. Twenty seconds.")

    # 8 verification
    s = new()
    _chrome(s, 11, total)
    _kicker(s, "03    Making")
    _tb(s, 0.48, 0.58, 6.2, 0.8, [{"text": "Then it was run", "size": 34, "bold": True}])
    _tb(s, 0.48, 1.7, 6.1, 4.8, [
        {"text": "The verify-skill drives the real Folio window, not a copy of the page in a browser.", "size": 20, "after": 14},
        {"text": "On 3 November the sandbox held nine lecture PDFs. A Chinese question for the JUnit annotation cited Lec-3.4, pages 3 and 2.", "size": 20, "after": 14},
        {"text": "The source title opened the indexed excerpt. The editor was not changed.", "size": 20, "after": 14},
        {"text": "The same run produced three exercises, kept a separate draft in each mode, and stopped a later answer on request.", "size": 20, "after": 14},
        {"text": "The picture is that excerpt.", "size": 20, "italic": True, "color": C_MUTED},
    ])
    _pic(s, "excerpt.png", 7.05, 0.85, 5.9)
    _notes(s, "This is the proof slide. The backend path is the citation you can open. Do not say every action was executed in that project: it was not a Git repository, so commit fill was not run there.")

    # 9 frontend
    s = new()
    _chrome(s, 12, total)
    _kicker(s, "03    Making")
    _tb(s, 0.48, 0.5, 12, 0.55, [{"text": "The window, after our notes", "size": 32, "bold": True}])
    _pic(s, "files.png", 0.4, 1.25, 5.55)
    _pic(s, "settings.png", 6.55, 1.55, 3.5)
    _tb(s, 6.55, 5.3, 6.2, 1.5, [
        {"text": "Left, the nine lectures.", "size": 16, "after": 2},
        {"text": "Right, settings. The key field stays empty. The line under it says a key for this address is saved.", "size": 16},
    ])
    _notes(s, "The interface work came after the retrieval path. We asked for readable type, a thinking block that is not the answer, notices that go away, and drafts that survive a mode change. Cursor clicked the window and sent these pictures. We replied from a phone.")

    # 10 demo opener
    s = new()
    _chrome(s, 13, total)
    _kicker(s, "04    Demonstration")
    _tb(s, 0.48, 0.62, 12, 0.7, [{"text": "What we will click", "size": 36, "bold": True}])
    rows = [
        ("1", "The nine PDFs."),
        ("2", "One question, left long enough to stream, then a citation opened."),
        ("3", "Three exercises. One explanation. The other two stay closed."),
        ("4", "Stop a second question while it is still thinking."),
        ("5", "Settings, then new chat, then cancel. The history remains."),
    ]
    for i, (n, line) in enumerate(rows):
        y = 1.7 + i * 0.95
        _tb(s, 0.55, y, 0.7, 0.6, [{"text": n, "size": 28, "bold": True, "color": C_BURG}])
        _tb(s, 1.4, y + 0.08, 11, 0.6, [{"text": line, "size": 24}])
    _notes(s, "Leave this up only while you switch to IntelliJ. Then do the five clicks. If the network is slow, the next three slides already hold the pictures.")

    # 14 files
    s = new()
    _chrome(s, 14, total)
    _kicker(s, "04    Demonstration")
    _tb(s, 0.48, 0.5, 6.2, 0.9, [{"text": "The lectures", "size": 34, "bold": True}])
    _tb(s, 0.48, 1.55, 5.8, 4.5, [
        {"text": "Lec-0 through Lec-5.", "size": 22, "after": 10},
        {"text": "Lec-3 is four files: exceptions, generics, annotations, JUnit.", "size": 22, "after": 10},
        {"text": "Indexing did not need a key. The pages shown are the pages the index stored.", "size": 22, "after": 10},
        {"text": "Removing a file asks first. The original PDF stays on disk.", "size": 22},
    ])
    _pic(s, "files.png", 6.7, 0.7, 6.15)
    _notes(s, "Scroll the list if you are live. Otherwise this picture is the list. Twenty seconds.")

    # 15 answer
    s = new()
    _chrome(s, 15, total)
    _kicker(s, "04    Demonstration")
    _tb(s, 0.42, 0.42, 12, 0.45, [{"text": "An answer, and the page it came from", "size": 30, "bold": True}])
    _pic(s, "citations.png", 0.4, 1.1, 5.7)
    _pic(s, "excerpt.png", 6.85, 1.1, 5.7)
    _notes(s, "Left: the question and two citations. Right: page 3 of Lec-3.4, opened from the first title. Say the marker [1] is the page, not a decoration.")

    # 16 drill
    s = new()
    _chrome(s, 16, total)
    _kicker(s, "04    Demonstration")
    _tb(s, 0.48, 0.48, 12, 0.5, [{"text": "Three exercises", "size": 32, "bold": True}])
    _pic(s, "drill.png", 0.4, 1.15, 5.7)
    _tb(s, 6.7, 1.4, 6.0, 4.8, [
        {"text": "The topic was checked and unchecked exceptions.", "size": 20, "after": 12},
        {"text": "The first explanation is open. The second and third are not.", "size": 20, "after": 12},
        {"text": "Two course citations sit under the set.", "size": 20, "after": 12},
        {"text": "This view appears when the three exercises parse. It does not paint tokens on the way.", "size": 20, "italic": True},
    ])
    _notes(s, "Be honest about streaming. Ask streams. Practice waits for valid JSON. If you want the room to see tokens, do it on the question, not on the drill.")

    # 14 close
    s = new()
    _chrome(s, 17, total)
    _kicker(s, "04    Demonstration")
    _tb(s, 0.48, 0.7, 12, 0.8, [{"text": "What the picture is not", "size": 34, "bold": True}])
    limits = [
        ("A shared word", "is not yet a correct sentence."),
        ("A thinking block", "is visible while an answer streams, and is gone when it finishes."),
        ("A test preview", "has been checked for syntax and annotations, not run."),
        ("A commit draft", "does not create a commit."),
    ]
    for i, (k, v) in enumerate(limits):
        y = 1.9 + i * 0.95
        _tb(s, 0.55, y, 4.2, 0.7, [{"text": k, "size": 22, "italic": True, "color": C_BURG}])
        _tb(s, 4.9, y, 7.6, 0.7, [{"text": v, "size": 22}])
    _tb(s, 0.55, 5.9, 12, 0.8, [{"text": "Folio.    罗臻    李灿文    范羽    孟圣雨", "size": 20}])
    _notes(s, "Close on these four sentences. Then take questions. If a test or a commit is asked for, do it only on a project that has Java source and a staged change.")

    prs.save(str(path))


def build_slide_pdf(path: Path) -> None:
    """Same fourteen slides, with an embedded Times-metric face."""
    c = pdfcanvas.Canvas(str(path), pagesize=PAGE)
    c.setTitle("Folio — CST3492 Group B, Team 4")
    c.setAuthor("Luo Zhen, Li Canwen, Fan Yu, Meng Shengyu")
    pw, ph = PAGE

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
        c.drawRightString(pw - 24, 7, f"{n}   /   17")

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

    def wrap(text, size, width, face="Serif"):
        words = text.split()
        lines, cur = [], ""
        for word in words:
            trial = word if not cur else cur + " " + word
            if c.stringWidth(trial, face, size) <= width:
                cur = trial
            else:
                lines.append(cur)
                cur = word
        if cur:
            lines.append(cur)
        return lines

    def image(name, x, y, h, max_w=900, center=False):
        path_img = str(FIG / name)
        with Image.open(path_img) as im:
            aspect = im.width / im.height
        w = h * aspect
        if w > max_w:
            w = max_w
            h = w / aspect
        if center:
            x = (pw - w) / 2
        c.setStrokeColor(HexColor("#1C1915"))
        c.setLineWidth(0.8)
        c.rect(x - 1, y - 1, w + 2, h + 2, fill=0, stroke=1)
        c.drawImage(path_img, x, y, w, h, preserveAspectRatio=True, mask="auto")
        return w

    # 1
    paper(); num(1)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 12)
    c.drawString(48, 470, "CST3492      ·      GROUP B      ·      TEAM 4")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 64)
    c.drawString(46, 390, "Folio")
    c.setFont("Serif-I", 18)
    c.drawString(50, 340, "A course assistant inside IntelliJ.")
    c.drawString(50, 316, "It answers from the lectures, and it shows the page.")
    c.setStrokeColor(HexColor("#7A3028"))
    c.setLineWidth(1.2)
    c.line(50, 280, 160, 280)
    members = [("2451989", "罗臻  Luo Zhen"), ("2452279", "李灿文  Li Canwen"), ("2452750", "范羽  Fan Yu"), ("2452272", "孟圣雨  Meng Shengyu")]
    for i, (sid, name) in enumerate(members):
        x = 50 + (i % 2) * 400
        y = 230 - (i // 2) * 58
        mixed(x, y, name, 18, "Serif-B")
        c.setFillColor(HexColor("#5E574E"))
        c.setFont("Serif", 12)
        c.drawString(x, y - 18, sid)
    c.showPage()

    # 2
    paper(); num(2)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "02     PROJECT")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 28)
    c.drawString(36, 460, "What it is")
    body = [
        "Folio lives in a tool window on the right of IntelliJ.",
        "It indexes the course files on the machine. A question retrieves a few pages and asks the model to answer from those pages.",
        "The reply names the file and the page. The page can be opened.",
        "Three further actions sit in the same window: practice, a JUnit preview, and commit messages.",
        "Group C owns rewriting source and the diff viewer. Folio does not.",
    ]
    y = 420
    c.setFont("Serif", 13)
    for para in body:
        for line in wrap(para, 13, 330):
            c.drawString(36, y, line)
            y -= 18
        y -= 10
    image("citations.png", 400, 55, 400)
    c.showPage()

    # 3
    paper(); num(3)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "02     PROJECT")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 28)
    c.drawString(36, 460, "What the brief required")
    blocks = [
        ("F0", "Course retrieval", "Cut the lectures into chunks. Index them. Retrieve the best. Cite the file and the page or the slide."),
        ("F1", "Three IDE actions", "A drill on the selection. A JUnit preview that is written only when asked. Commit messages from the staged diff."),
        ("NOT OURS", "Group C", "Changing the student’s code from an instruction, and driving the built-in diff viewer."),
    ]
    y = 400
    for k, title, body in blocks:
        c.setFillColor(HexColor("#7A3028"))
        c.rect(36, y - 48, 4, 70, fill=1, stroke=0)
        c.setFont("Serif-B", 11)
        c.drawString(56, y + 16, k)
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif-B", 18)
        c.drawString(56, y - 8, title)
        c.setFont("Serif", 13)
        c.setFillColor(HexColor("#5E574E"))
        c.drawString(56, y - 30, body[:110])
        y -= 110
    c.showPage()

    # 4
    paper(); num(4)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "03     MAKING")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 26)
    c.drawString(36, 466, "How it was made")
    image("process.png", 28, 40, 400)
    c.showPage()

    # 5
    paper(); num(5)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "03     MAKING")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 26)
    c.drawString(36, 462, "Decisions that stayed with us")
    c.setFont("Serif-I", 14)
    c.drawString(36, 430, "After the restatement, Cursor named the choices it would not make alone.")
    decisions = [
        ("Retrieval", "A local word index, not an embedding service."),
        ("The third action", "A course drill, not a rewrite and not another answer."),
        ("Tests", "A preview first. The file is written only on a click."),
        ("Commits", "Show the message. Do not commit, and do not touch the index."),
        ("The model", "The address stays in settings. This run uses DeepSeek."),
    ]
    y = 370
    for k, v in decisions:
        c.setFillColor(HexColor("#7A3028"))
        c.setFont("Serif-B", 15)
        c.drawString(36, y, k)
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif", 15)
        c.drawString(230, y, v)
        y -= 52
    c.showPage()

    def chart_page(n, title, image_name, caption):
        paper(); num(n)
        c.setFillColor(HexColor("#7A3028"))
        c.setFont("Serif-B", 11)
        c.drawString(36, 500, "03     MAKING")
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif-B", 20)
        c.drawString(36, 472, title)
        c.setFillColor(HexColor("#5E574E"))
        c.setFont("Serif-I", 11)
        c.drawString(36, 452, caption)
        image(image_name, 30, 32, 400, max_w=900, center=True)
        c.showPage()

    chart_page(6, "Ownership, from the repository", "ownership.png",
               "docs/images/components.svg. Who owns the library, the key, and a write.")
    chart_page(7, "Tool window, checked with TLC", "state-window.png",
               "docs/statechart. One activity at a time. A key is required before Ask, Preview, or Draft.")
    chart_page(8, "Retrieval, checked with TLC", "state-retrieval.png",
               "A page citation exists only after a hit. A miss ends as general knowledge. Stop invents no page.")
    chart_page(9, "Test write, checked with TLC", "state-test.png",
               "Cancel writes nothing. Write is reachable only from a Java preview.")
    chart_page(10, "Commit delivery, checked with TLC", "state-commit.png",
               "Copy and fill require the captured staging. The model never creates a commit.")

    # 8
    paper(); num(11)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "03     MAKING")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 26)
    c.drawString(36, 460, "Then it was run")
    paras = [
        "The verify-skill drives the real Folio window.",
        "Nine lecture PDFs were indexed. A Chinese question for the JUnit annotation cited Lec-3.4, pages 3 and 2.",
        "The source title opened the excerpt. The editor did not change.",
        "The picture is that excerpt.",
    ]
    y = 410
    c.setFont("Serif", 13)
    for para in paras:
        for line in wrap(para, 13, 340):
            c.drawString(36, y, line)
            y -= 18
        y -= 12
    image("excerpt.png", 400, 55, 400)
    c.showPage()

    # 9
    paper(); num(12)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "03     MAKING")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 24)
    c.drawString(36, 466, "The window, after our notes")
    image("files.png", 28, 48, 390)
    image("settings.png", 470, 180, 220)
    c.setFont("Serif", 12)
    c.drawString(470, 150, "Left, the nine lectures.")
    for i, line in enumerate(wrap("Right, settings. The key field stays empty.", 12, 420)):
        c.drawString(470, 130 - i * 16, line)
    c.showPage()

    # 10
    paper(); num(13)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "04     DEMONSTRATION")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 28)
    c.drawString(36, 458, "What we will click")
    rows = [
        "The nine PDFs.",
        "One question, left long enough to stream, then a citation opened.",
        "Three exercises. One explanation. The other two stay closed.",
        "Stop a second question while it is still thinking.",
        "Settings, then new chat, then cancel.",
    ]
    y = 390
    for i, line in enumerate(rows, 1):
        c.setFillColor(HexColor("#7A3028"))
        c.setFont("Serif-B", 20)
        c.drawString(40, y, str(i))
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif", 16)
        c.drawString(80, y, line)
        y -= 52
    c.showPage()

    # 14
    paper(); num(14)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "04     DEMONSTRATION")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 26)
    c.drawString(36, 462, "The lectures")
    c.setFont("Serif", 14)
    y = 410
    for para in ["Lec-0 through Lec-5.", "Lec-3 is four files.", "Indexing did not need a key.", "Removing a file asks first."]:
        c.drawString(36, y, para)
        y -= 36
    image("files.png", 360, 40, 420)
    c.showPage()

    # 12
    paper(); num(15)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "04     DEMONSTRATION")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 22)
    c.drawString(36, 468, "An answer, and the page it came from")
    image("citations.png", 30, 40, 400)
    image("excerpt.png", 490, 40, 400)
    c.showPage()

    # 13
    paper(); num(16)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "04     DEMONSTRATION")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 24)
    c.drawString(36, 466, "Three exercises")
    image("drill.png", 28, 40, 400)
    c.setFont("Serif", 13)
    notes = [
        "Checked and unchecked exceptions.",
        "The first explanation is open.",
        "The other two stay closed.",
        "This view appears when the",
        "three exercises parse.",
    ]
    y = 400
    for line in notes:
        c.drawString(470, y, line)
        y -= 28
    c.showPage()

    # 14
    paper(); num(17)
    c.setFillColor(HexColor("#7A3028"))
    c.setFont("Serif-B", 11)
    c.drawString(36, 500, "04     DEMONSTRATION")
    c.setFillColor(HexColor("#1C1915"))
    c.setFont("Serif-B", 26)
    c.drawString(36, 458, "What the picture is not")
    limits = [
        ("A shared word", "is not yet a correct sentence."),
        ("A thinking block", "shows while streaming, then goes."),
        ("A test preview", "is syntax and annotations, not a run."),
        ("A commit draft", "does not create a commit."),
    ]
    y = 380
    for k, v in limits:
        c.setFillColor(HexColor("#7A3028"))
        c.setFont("Serif-I", 16)
        c.drawString(36, y, k)
        c.setFillColor(HexColor("#1C1915"))
        c.setFont("Serif", 16)
        c.drawString(250, y, v)
        y -= 52
    mixed(36, 80, "Folio.     罗臻    李灿文    范羽    孟圣雨", 16, "Serif")
    c.save()


if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    build_pptx(ROOT / "Folio-GroupB-Team4-Slides.pptx")
    build_slide_pdf(ROOT / "Folio-GroupB-Team4-Slides.pdf")
    print("slides written")
