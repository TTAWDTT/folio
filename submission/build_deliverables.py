#!/usr/bin/env python3
"""Build the CST3492 Group B Team 4 report PDF, slide PDF, and PowerPoint."""

from __future__ import annotations

import html
from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.oxml.ns import qn
from pptx.util import Emu, Inches, Pt
from reportlab.lib.colors import Color, HexColor, white
from reportlab.lib.enums import TA_JUSTIFY, TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from PIL import Image as PILImage
from reportlab.platypus import (
    BaseDocTemplate,
    CondPageBreak,
    Frame,
    Image,
    KeepTogether,
    ListFlowable,
    ListItem,
    NextPageTemplate,
    PageBreak,
    PageTemplate,
    Paragraph,
    Preformatted,
    Spacer,
    Table,
    TableStyle,
)

ROOT = Path("/workspace/submission/documents")
FONT = "/usr/share/fonts/truetype/macos"
pdfmetrics.registerFont(TTFont("Inter", f"{FONT}/Inter-Regular.ttf"))
pdfmetrics.registerFont(TTFont("Inter-Med", f"{FONT}/Inter-Medium.ttf"))
pdfmetrics.registerFont(TTFont("Inter-Semi", f"{FONT}/Inter-SemiBold.ttf"))
pdfmetrics.registerFont(TTFont("Inter-Bold", f"{FONT}/Inter-Bold.ttf"))
pdfmetrics.registerFont(TTFont("CJK", "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", subfontIndex=0))
pdfmetrics.registerFont(TTFont("Mono", "/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Regular.ttf"))

NAVY = HexColor("#0E3A5D")
INK = HexColor("#1C2428")
MUTED = HexColor("#5C6B73")
RULE = HexColor("#D7D1C7")
PAPER = HexColor("#F7F4EE")
CARD = HexColor("#FFFDF9")
GOLD = HexColor("#8C6A32")
PALE = HexColor("#E7EEF3")

TEAM = [
    ("2451989", "罗臻", "Luo Zhen"),
    ("2452270", "李灿文", "Li Canwen"),
    ("2452750", "范羽", "Fan Yu"),
    ("2452272", "孟圣雨", "Meng Shengyu"),
]


def esc(text: str) -> str:
    return html.escape(text, quote=False)


def zh(text: str) -> str:
    return f'<font name="CJK">{esc(text)}</font>'


def styles() -> dict[str, ParagraphStyle]:
    return {
        "h1": ParagraphStyle("h1", fontName="Inter-Bold", fontSize=15, leading=19, textColor=NAVY, spaceBefore=11, spaceAfter=4, keepWithNext=True),
        "h2": ParagraphStyle("h2", fontName="Inter-Semi", fontSize=12, leading=15, textColor=NAVY, spaceBefore=8, spaceAfter=3, keepWithNext=True),
        "body": ParagraphStyle("body", fontName="Inter", fontSize=10.5, leading=14.6, textColor=INK, alignment=TA_JUSTIFY, spaceAfter=6),
        "bullet": ParagraphStyle("bullet", fontName="Inter", fontSize=10.5, leading=14.2, textColor=INK, leftIndent=2),
        "cell": ParagraphStyle("cell", fontName="Inter", fontSize=9, leading=12, textColor=INK),
        "cellh": ParagraphStyle("cellh", fontName="Inter-Semi", fontSize=9, leading=12, textColor=white),
        "caption": ParagraphStyle("caption", fontName="Inter", fontSize=8.5, leading=11, textColor=MUTED, spaceBefore=1, spaceAfter=8),
        "mono": ParagraphStyle("mono", fontName="Mono", fontSize=8, leading=11, textColor=INK),
        "cover_kicker": ParagraphStyle("ck", fontName="Inter-Semi", fontSize=11, leading=14, textColor=HexColor("#D7C4A1"), tracking=1.2),
        "cover_title": ParagraphStyle("ct", fontName="Inter-Bold", fontSize=34, leading=38, textColor=white),
        "cover_sub": ParagraphStyle("cs", fontName="Inter", fontSize=13, leading=18, textColor=HexColor("#E6EDF2")),
        "cover_meta": ParagraphStyle("cm", fontName="Inter", fontSize=11, leading=16, textColor=HexColor("#E6EDF2")),
    }


S = styles()


def P(text: str, style: str = "body") -> Paragraph:
    return Paragraph(text, S[style])


def bullets(items: list[str]) -> ListFlowable:
    return ListFlowable(
        [ListItem(Paragraph(item, S["bullet"]), leftIndent=10, bulletColor=NAVY) for item in items],
        bulletType="bullet",
        start="•",
        leftIndent=14,
        bulletFontName="Inter",
        bulletFontSize=10,
        spaceBefore=1,
        spaceAfter=6,
    )


def figure(name: str, caption: str, page_width: float, max_height: float = 195 * mm):
    path = ROOT / "figures" / name
    with PILImage.open(path) as im:
        pixel_w, pixel_h = im.size
    scale = min(page_width / pixel_w, max_height / pixel_h)
    img = Image(str(path), width=pixel_w * scale, height=pixel_h * scale)
    img.hAlign = "CENTER"
    return KeepTogether([img, P(caption, "caption")])


def table(headers: list[str], rows: list[list[str]], widths: list[float]) -> Table:
    data = [[Paragraph(esc(h), S["cellh"]) for h in headers]]
    for row in rows:
        data.append([Paragraph(cell, S["cell"]) for cell in row])
    tbl = Table(data, colWidths=widths, repeatRows=1)
    tbl.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NAVY),
        ("TEXTCOLOR", (0, 0), (-1, 0), white),
        ("BACKGROUND", (0, 1), (-1, -1), CARD),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [CARD, PALE]),
        ("FONTNAME", (0, 0), (-1, 0), "Inter-Semi"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("LINEBELOW", (0, 0), (-1, 0), 0, NAVY),
        ("LINEBELOW", (0, 1), (-1, -2), 0.2, RULE),
        ("BOX", (0, 0), (-1, -1), 0.4, NAVY),
    ]))
    return tbl


class Pipeline(KeepTogether):
    pass


def cover_page(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(NAVY)
    canvas.rect(0, 0, A4[0], A4[1], fill=1, stroke=0)
    canvas.setFillColor(GOLD)
    canvas.rect(0, 0, 8 * mm, A4[1], fill=1, stroke=0)
    canvas.restoreState()


def content_page(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(PAPER)
    canvas.rect(0, 0, A4[0], A4[1], fill=1, stroke=0)
    canvas.setFillColor(NAVY)
    canvas.rect(0, A4[1] - 12 * mm, A4[0], 12 * mm, fill=1, stroke=0)
    canvas.setFillColor(GOLD)
    canvas.rect(0, A4[1] - 12.8 * mm, A4[0], 0.8 * mm, fill=1, stroke=0)
    canvas.setFillColor(white)
    canvas.setFont("Inter", 8)
    canvas.drawString(18 * mm, A4[1] - 7.6 * mm, "FOLIO   ·   CST3492 GROUP B   ·   TEAM 4")
    canvas.setFillColor(MUTED)
    canvas.setFont("Inter", 8)
    canvas.drawString(18 * mm, 10 * mm, "Course retrieval and three IDE actions")
    canvas.drawRightString(A4[0] - 18 * mm, 10 * mm, str(doc.page))
    canvas.restoreState()


def build_report(path: Path) -> None:
    doc = BaseDocTemplate(
        str(path),
        pagesize=A4,
        title="Folio — CST3492 Group B, Team 4",
        author="Luo Zhen, Li Canwen, Fan Yu, Meng Shengyu",
    )
    cover = PageTemplate(id="cover", frames=[Frame(22 * mm, 22 * mm, A4[0] - 40 * mm, A4[1] - 44 * mm, showBoundary=0)], onPage=cover_page)
    body = PageTemplate(id="body", frames=[Frame(18 * mm, 18 * mm, A4[0] - 36 * mm, A4[1] - 36 * mm, showBoundary=0)], onPage=content_page)
    doc.addPageTemplates([cover, body])
    width = A4[0] - 36 * mm
    story: list = []

    story.append(Spacer(1, 48 * mm))
    story.append(P("CST3492  ·  GROUP B  ·  TEAM 4", "cover_kicker"))
    story.append(Spacer(1, 8 * mm))
    story.append(P("Folio", "cover_title"))
    story.append(Spacer(1, 4 * mm))
    story.append(P("An IntelliJ IDEA plugin that answers from indexed course files and adds three IDE actions.", "cover_sub"))
    story.append(Spacer(1, 14 * mm))
    story.append(P("Project report", "cover_sub"))
    story.append(Spacer(1, 8 * mm))
    for sid, name, roman in TEAM:
        story.append(P(f"{sid}&nbsp;&nbsp;&nbsp;{zh(name)}&nbsp;&nbsp;{esc(roman)}", "cover_meta"))
    story.append(Spacer(1, 12 * mm))
    story.append(P("Plugin cst3492.folio 0.1.0<br/>IntelliJ IDEA 2026.2.4<br/>Revision 16ac42b<br/>Demonstration window: 4 November 2026, 1:30–4:15pm", "cover_meta"))
    story.append(NextPageTemplate("body"))
    story.append(PageBreak())

    story.append(P("1. Members"))
    story.append(P("This report is the Group B submission for Team 4. Group B builds course retrieval and three smart actions inside the IDE. Group C owns source rewriting and the built-in diff viewer. Folio does not implement that second scope."))
    story.append(table(
        ["Student ID", "Name", "Name in Latin letters"],
        [[sid, zh(name), roman] for sid, name, roman in TEAM],
        [width * 0.28, width * 0.32, width * 0.40],
    ))
    story.append(P("The brief asks for one English report and one English slide deck, in this order: members, feature list, user manual, architecture including chunking and indexing, then any further notes. Section 5 is that further note: how the team and Cursor made this version. The accompanying ZIP keeps the source tree in one folder and these two PDFs in another. Canvas asks for that ZIP before 11:00pm on 3 November 2026. The brief does not name a time zone. The team is working in Asia/Shanghai.", "caption"))

    story.append(P("2. Feature list"))
    story.append(P("The list below is what this revision does, matched to the brief. Labels in Chinese are the labels in the tool window."))
    story.append(P("2.1 Course retrieval", "h2"))
    story.append(bullets([
        "<b>Import.</b> " + zh("导入课件") + " accepts PDF, PPTX, TXT, Markdown, and DOCX. Indexing does not need an API key. The library is shared by projects in the same IDE configuration.",
        "<b>Chunk.</b> A PDF keeps original page numbers, including blank-page gaps. A PPTX keeps slide order, nested groups, table cells, and notes, and drops slide-master text. Text files are sections, without invented page numbers.",
        "<b>Split.</b> Each page, slide, or section is then split at 700 Java characters with a 100-character overlap.",
        "<b>Index.</b> Retrieval is local BM25. There is no embedding model and no web search. A question returns at most four chunks.",
        "<b>Ask.</b> " + zh("问答") + " in the composer, and <b>Ask Folio</b> on an editor selection. With no saved key, local hits still appear and Folio makes no HTTP call.",
        "<b>Cite.</b> Completed replies map prose markers such as [1] onto the retrieved chunks. The source title opens a text file near the excerpt, or an indexed preview for a PDF or slide.",
        "<b>Stream.</b> An Ask request appends incoming answer text as it arrives. Reasoning tokens stay in a separate " + zh("思考") + " block. " + zh("停止生成") + " keeps the answer text already received.",
    ]))
    story.append(P("2.2 Three IDE actions", "h2"))
    story.append(bullets([
        "<b>Course drill,</b> " + zh("练习") + " or <b>Folio Course Drill</b>. Three exercises, each with its own " + zh("查看解析") + ". A malformed reply is reported. An empty selection makes no model call. The editor is not modified.",
        "<b>JUnit preview,</b> <b>Generate JUnit From Scenario</b> or " + zh("生成测试") + ". The scenario is collected before any model call. The preview separates the raw reply from the Java. " + zh("写入文件") + " is the only write. Generation alone does not create a file.",
        "<b>Commit candidates,</b> <b>Draft Folio Commit Messages</b> or " + zh("提交信息") + ". Folio reads the staged diff, shows candidates, and copies or fills one only after an explicit click. It does not run git commit and it does not change the index.",
    ]))
    story.append(P("2.3 Outside this version", "h2"))
    story.append(P("Folio does not rewrite project source from a free-form instruction, and it does not open the IDE diff viewer as an editing surface. Those belong to Group C. A drill does not paint model tokens: the panel stays on a phase line until three exercises parse. The stopped card keeps answer text and does not keep the thinking block."))

    story.append(P("3. User manual"))
    story.append(P("Open a course project in IntelliJ IDEA 2026.2.4 with the Folio plugin installed. The <b>Folio</b> tool window sits on the right."))
    story.append(P("3.1 Bring in the lectures", "h2"))
    story.append(bullets([
        "Choose the folder icon, " + zh("课件") + ".",
        "Choose " + zh("选择课件并导入") + " and select the lecture files. PDF, PPTX, and text are the formats with direct coverage.",
        "Wait until each row shows a title, a path, and a page, slide, or section count. Re-index and remove are on the row. Remove asks for confirmation because the library is shared. The original file stays on disk.",
        zh("返回对话") + " restores the conversation, the draft, and the reading position.",
    ]))
    story.append(P("3.2 Ask, and read the citation", "h2"))
    story.append(bullets([
        "Leave the mode on " + zh("问答") + ". Type a question. Enter sends. Shift+Enter inserts a line. Unconfirmed IME text does not send.",
        "While the model is working, the same round button is " + zh("停止生成") + ". Stopping keeps the text already on screen and the draft typed for the next turn.",
        "After the reply, expand " + zh("课件 · N 处引用") + ". Each row shows the file, the page or slide, and a short excerpt. Choose the blue title to open the excerpt. Choosing the excerpt text does not navigate.",
        "A question with no usable chunk says " + zh("没有命中课件，以下内容不是课件引用。") + " Folio does not invent a page number for that reply.",
        "Without a saved key, the same retrieval still runs. The panel states: “No API key is configured. Add an API key in the Folio tool window and save settings.”",
    ]))
    story.append(P("3.3 Save the model connection", "h2"))
    story.append(bullets([
        "Open " + zh("设置") + ". The fields are API address, model, and API key.",
        "This version’s saved defaults are https://api.deepseek.com and deepseek-flash. Requests go to /chat/completions.",
        "The key field stays empty when a key is already saved. The line under it says the key for this address is saved. An empty key on save keeps that credential. Choose " + zh("移除") + " only when the save should delete it.",
        "The first save may ask the operating system for a keychain password. " + zh("取消") + " or " + zh("返回对话") + " drops the unsaved edit. A successful save does not prove that the endpoint is reachable.",
    ]))
    story.append(P("3.4 Switch task without losing the draft", "h2"))
    story.append(P("The mode menu is " + zh("选择模式") + ". The four modes are " + zh("课程问答") + ", " + zh("课程练习") + ", " + zh("生成测试") + ", and " + zh("提交信息") + ". Each mode keeps its own unsent draft. History stays in one conversation. " + zh("新对话") + " asks before it clears that history. Unsent drafts survive a new conversation."))
    story.append(P("3.5 Practice", "h2"))
    story.append(P("Choose " + zh("练习") + " and name a topic, or select Java code and use <b>Folio Course Drill</b>. The result is three exercises. Open one " + zh("查看解析") + " at a time. The other explanations stay closed until opened. Course citations, when the reply used them, sit under the exercises."))
    story.append(P("3.6 Generate a test, then write it", "h2"))
    story.append(bullets([
        "On a Java class or method, choose <b>Generate JUnit From Scenario</b>. Cancel, or an empty scenario, makes no request.",
        "Or attach Java context and use " + zh("生成测试") + ".",
        "Open " + zh("查看测试预览") + ". Read " + zh("Java 预览") + " and " + zh("原始回复") + ". The preview says the check is PSI syntax and JUnit recognition only.",
        "Choose " + zh("写入文件") + " when the target path is acceptable. Folio checks the source, the directory, and the file name again, then creates the file through an IDE write command. Undo works. The model is not called a second time.",
    ]))
    story.append(P("3.7 Draft a commit message", "h2"))
    story.append(bullets([
        "Stage the changes you want described. Folio does not look at unstaged edits.",
        "Choose " + zh("提交信息") + ", or <b>Draft Folio Commit Messages</b> from the commit dialog.",
        "A project that is not a Git repository, and a repository with an empty index, are explained without a model call.",
        "Read the candidates and the captured diff. " + zh("复制提交信息") + " copies one candidate. " + zh("填入提交框") + " fills the original commit box when that box is still the one Folio captured.",
        "Neither action creates a commit. If the staging changed after the draft, Folio refuses the copy or the fill.",
    ]))

    story.append(P("4. Architecture"))
    story.append(P("Folio is one IntelliJ plugin. The tool window is a JCEF page. Java owns the library, retrieval, model calls, and IDE writes. The page owns presentation."))
    story.append(P("4.1 Path of one question", "h2"))
    story.append(table(
        ["Stage", "What happens"],
        [
            ["1. Parse", "Tika 4.2.0 reads each file into pages, slides, or sections."],
            ["2. Split", "Long units are cut at 700 characters with a 100-character overlap."],
            ["3. Store", "The chunks are committed to folio/index.json."],
            ["4. Retrieve", "BM25 takes the question and the selection and returns at most four chunks."],
            ["5. Generate", "DeepSeek streams the answer and, separately, the reasoning."],
            ["6. Cite", "After the reply finishes, prose markers map onto those chunks."],
        ],
        [width * 0.22, width * 0.78],
    ))
    story.append(Spacer(1, 3 * mm))
    story.append(P("The tool window talks to FolioSession. FolioSession holds the conversation, the four drafts, the route, and the active generation. CourseLibrary parses a batch and commits it under a lock. Asker retrieves, calls the model, and maps citations. Drill, the test preview, and the commit draft are separate requests on that same session.", "caption"))

    story.append(P("4.2 Chunking", "h2"))
    story.append(P("Tika runs with the plugin class loader, so the PDF and PPTX parsers inside the plugin are visible. PDF text is taken per page, and the stored number is the original page number. PPTX text is taken per slide, including nested shapes, tables, and notes. Master-slide text is excluded, because it would crowd every query. TXT, Markdown, and DOCX use Tika’s non-paginated body and are labeled as sections."))
    story.append(P("CourseChunks then cuts a long page. The limit is 700 Java characters. The overlap is 100. The cut prefers a space or newline in the second half of the window, and it does not split a UTF-16 surrogate pair. A split piece keeps the original page or slide number and gains a part identifier. Empty text is not stored."))
    story.append(P("The committed library lives in the IDE configuration directory:"))
    story.append(table(
        ["File", "Role"],
        [
            ["folio/index.json", "Versioned chunks, library revision, and source revisions."],
            ["folio/index.backup.json", "The previous valid JSON file."],
            ["folio/index.lock", "Coordinates readers and writers."],
            ["folio/index.tsv", "Legacy import input. Migration keeps the original."],
        ],
        [width * 0.38, width * 0.62],
    ))
    story.append(Spacer(1, 3 * mm))
    story.append(P("A batch is parsed and checked before it is committed. A missing file, a parser failure, a file with no text, or a cancel before commit leaves the old library in place. Sources are absolute paths, so two files with the same name in different directories stay distinct."))

    story.append(P("4.3 Index and retrieval", "h2"))
    story.append(P("CourseIndex.top ranks chunks with BM25, k1 = 1.2 and b = 0.75. There is no vector index."))
    story.append(bullets([
        "English terms are lowercase words, with a stop list and light stemming. Courtesy words and generic task verbs drop out of the coverage test when more specific terms remain. Those verbs stay searchable on their own.",
        "Chinese, Japanese, and Korean text is indexed as overlapping bigrams. Chinese coverage groups a run of content characters and removes listed courtesy phrases.",
        "A chunk must cover at least half of the question’s coverage groups. Scores below 25 percent of the best remaining score are dropped. The caller asks for at most four chunks.",
        "A Chinese question about an English lecture used to fail that half-coverage test, because the Chinese bigrams are not in the slides. This revision ignores unattested Chinese groups when the question also has Latin terms and those Latin terms do occur in the index. The English terms still have to cover half of their own groups.",
    ]))
    story.append(P("History is quoted context for the model. It is not citation evidence. Retrieval uses the current question and the current selection. The next request carries up to eight completed Ask turns, inside 8,000 characters. Drills, failures, and configuration-only replies do not become Ask history."))

    story.append(P("4.4 Streaming, then citations", "h2"))
    story.append(P("HttpChatClient reads the server-sent stream. Answer tokens and reasoning tokens stay separate. DeepSeek exposes the latter as reasoning_content. FolioSession forwards each answer fragment as a text change at a known offset, and republishes the card when the phase or the reasoning changes. The page appends the fragment onto the open stream card. It does not reveal a finished answer one character at a time."))
    story.append(P("Asker.mapCitations runs after the reply is complete. A prose marker is kept when its number names a retrieved chunk, then renumbered in first-use order. The same original number keeps the same new number. Markers inside code, calls, and expression lists stay literal. A marker that names nothing retrieved is removed from prose. If a literal number collides with citation numbering, Folio keeps the in-range numbers, lists every retrieved chunk, and marks the evidence ambiguous."))
    story.append(P("The evidence label describes that mapping. It does not judge whether the prose follows from the slide."))
    story.append(KeepTogether([table(
        ["Label", "Meaning in this version"],
        [
            ["GENERAL", "No course chunk supports the question. The fixed sentence says the reply is not a course citation."],
            ["INLINE", "The reply’s prose markers map onto retrieved chunks."],
            ["RETRIEVED", "Chunks were supplied, and the reply did not map a prose marker."],
            ["INVALID", "Out-of-range prose markers were removed."],
            ["AMBIGUOUS", "A literal number conflicts with citation numbering."],
            ["CONFIGURATION", "No model call. Local hits can still be shown."],
        ],
        [width * 0.28, width * 0.72],
    )]))
    story.append(Spacer(1, 3 * mm))
    story.append(P("The drill path uses the same retrieval and the same HTTP stream, then keeps only the phase updates for the panel. Token text waits until the reply parses as three questions with three explanations. That is why a practice turn appears all at once, while an Ask turn grows on screen."))

    story.append(P("4.5 Settings, tests, and commits", "h2"))
    story.append(P("The address and the model live in the application file folio.xml. The API key lives in IntelliJ Password Safe. The saved record stores the scheme, host, and port, not the secret. A later request uses the saved connection. Unsaved form fields do not affect a call already in flight. Errors redact the key."))
    story.append(P("A test preview is parsed with IntelliJ PSI. The check looks for Java syntax and common JUnit annotations. It does not compile, discover, or run the test. The write uses the captured source and the captured directory, and it refuses a closed project, a read-only directory, a package mismatch, or an existing file name."))
    story.append(P("A commit draft captures every configured Git root, including renames, deletions, and the full staged patch. Large diffs are sent in ordered batches of at most 12,000 Java characters, then one synthesis call. Nothing is silently trimmed. Copy and fill recheck the staging fingerprint before they touch the clipboard or the commit box."))

    story.append(P("4.6 Drawings", "h2"))
    story.append(P("Two drawings were already in the repository. Three more were checked with TLC and drawn with the statechart tool. TLC reported no broken rule on those three models. A citation exists only after a retrieved chunk. A test file is a copy of a preview that parsed as Java. Copying or filling a commit message requires the staging that was captured, and no step creates a commit."))
    story.append(figure(
        "ownership.png",
        "Figure 1. Ownership and boundaries, from docs/images/components.svg. Project tasks authorize writes. The course library and the key are shared. Workers do not read Swing fields.",
        width,
    ))
    story.append(figure(
        "state-window.png",
        "Figure 2. The Folio tool window, from docs/statechart. One activity at a time. Ask, a test preview, and a commit draft each require a saved or typed key. Write is reachable only from a preview that parsed as a test.",
        width,
        220 * mm,
    ))
    story.append(figure(
        "state-retrieval.png",
        "Figure 3. Course retrieval. Fourteen states. A miss finishes as general knowledge. A hit can finish as an answer with a page. Stopping does not invent a page.",
        width,
        220 * mm,
    ))
    story.append(figure(
        "state-test.png",
        "Figure 4. Test preview and write. Six states. Cancel returns to the selection and writes nothing. The file appears only after you click Write on a Java preview.",
        width,
    ))
    story.append(figure(
        "state-commit.png",
        "Figure 5. Commit candidates. Nine states. Copy and fill run only while the captured staging is unchanged. After the index changes, both are refused. The model contains no commit.",
        width,
        220 * mm,
    ))

    story.append(P("5. How this version was made"))
    story.append(P("The plugin was made as a conversation with Cursor cloud agents. The team read each result on a phone and answered there. The agent did not decide the points that belonged to the team."))
    story.append(figure(
        "process.png",
        "Figure 6. The eight steps, from the brief to the phone.",
        width,
        90 * mm,
    ))
    story.append(bullets([
        "<b>Restate.</b> The project brief was given to Cursor, with the instruction to restate it in its own words. The team read that restatement before any design was treated as settled.",
        "<b>Divide.</b> The restatement was turned into project points and modules. Where a choice needed a person, Cursor asked and waited.",
        "<b>Draw.</b> Cursor used an architecture pass for the ownership of the library, the key, and the writes, and used statecharts for the tool window. The retrieval, test-write, and commit charts in section 4.6 were drawn the same way and checked with TLC.",
        "<b>Revise.</b> The team reviewed the drawings and made small changes before implementation continued.",
        "<b>Prove.</b> Cursor wrote a verify-skill for this plugin. It drives the Folio window inside IntelliJ, not a page opened in a separate browser.",
        "<b>Build.</b> The code followed the architecture. The skill was then run on the sandbox. Retrieval, citation, and the opened excerpt worked on the nine lecture PDFs.",
        "<b>Shape.</b> The interface was changed from the team’s notes: readable type, a thinking block kept apart from the answer, notices that leave, and a draft that stays with its mode. After each change, Cursor clicked the window and returned screenshots.",
        "<b>Reply.</b> Every one of those steps ran on a Cursor cloud agent. The team did not need the IDE in front of them to accept or reject a result.",
    ]))
    story.append(P("The choices that stayed with the team were these. Retrieval is a local word index, not an embedding service, so a citation can still appear when the network is down. The third action is a course drill, not a rewrite of the student’s code and not a second answer. A test is previewed, then written only by an explicit click. A commit message is shown, and Folio does not run git commit. The model address stays in settings. This demonstration uses DeepSeek."))

    story.append(P("6. Other notes"))
    story.append(P("6.1 What this revision was run against", "h2"))
    story.append(P("On 3 November 2026 the sandbox IDE loaded revision 16ac42b and indexed nine lecture PDFs. The tool window reported these page counts:"))
    story.append(table(
        ["File", "Pages shown"],
        [
            ["Lec-0-Introduction.pdf", "4"],
            ["Lec-1-Java-Fundamentals.pdf", "34"],
            ["Lec-2-OOP-with-Java.pdf", "32"],
            ["Lec-3.1-Exceptions.pdf", "11"],
            ["Lec-3.2-Generics-and-Collections.pdf", "17"],
            ["Lec-3.3-ModernSyntax-Annotations-and-Reflection.pdf", "16"],
            ["Lec-3.4-JUnit.pdf", "7"],
            ["Lec-4-AI-Assisted-Java-Development.pdf", "34"],
            ["Lec-5-Building-AI-Powered-Java-Applications.pdf", "44"],
        ],
        [width * 0.78, width * 0.22],
    ))
    story.append(Spacer(1, 3 * mm))
    story.append(P("A Chinese question, “" + zh("根据课件，JUnit 5 的测试方法通常用哪个注解？请引用页码。") + "”, retrieved Lec-3.4-JUnit.pdf and cited page 3 and page 2. The answer named @Test. Opening the first citation showed the indexed excerpt for page 3, including a method marked @Test."))
    story.append(P("A practice request, “" + zh("比较") + " checked " + zh("与") + " unchecked exception”, returned three exercises. The first explanation opened on its own. The other two stayed closed. Two course citations remained under the set."))
    story.append(P("A later Ask about Java generics showed a separate thinking block and the phase " + zh("正在思考") + " before answer text. Stopping that turn left " + zh("已停止生成") + ". Drafts typed in " + zh("问答") + ", " + zh("生成测试") + ", and " + zh("提交信息") + " were still there after switching away and back. Settings showed the DeepSeek address and deepseek-flash, an empty key field, and the saved-key line. Cancel left the conversation in place. New chat asked, and cancel kept the history."))
    story.append(P("That live pass did not write a test file and did not fill a commit box. The demonstration project used for retrieval was not a Git repository, so commit generation had nothing staged to describe. Both actions are in this revision and follow the rules in section 3."))

    story.append(P("6.2 Limits worth saying in the room", "h2"))
    story.append(bullets([
        "BM25 can miss a paraphrase that shares no terms with the slide. It can also rank a chunk that shares terms and does not answer the question. The citation shows where the terms matched. It is not a proof that the sentence is right.",
        "The thinking block is visible while a reply streams. The finished card and the stopped card do not keep it.",
        "Practice waits for valid JSON. A bad shape is a failure with the raw reply, not three invented questions.",
        "The JUnit check is syntactic. Run the project’s test command before treating the file as a passing test.",
        "Commit generation does not promise that three candidates come back. It asks for three. The provider decides how many it returns.",
    ]))
    story.append(P("6.3 Build", "h2"))
    story.append(P("The plugin is built with the IntelliJ Platform Gradle Plugin 2.19.0, JDK 25, and IntelliJ IDEA 2026.2.4. sinceBuild is 262. Tika is 4.2.0. Run ./gradlew runIde from the source tree to open the sandbox. The plugin id is cst3492.folio and the version is 0.1.0."))
    story.append(P("6.4 Twenty minutes", "h2"))
    story.append(P("The talk has four parts: the name and the team, a short introduction, how the plugin was made, and the demonstration. The making section is the conversation with Cursor: restate the brief, split the work, draw the architecture, write a verification skill, test the backend, then change the interface from our notes. The demonstration uses the live tool window. Test writing and commit fill need a Java source and a staged Git repository. Show them if that project is open."))

    doc.build(story)



def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    report = ROOT / "Folio-GroupB-Team4-Report.pdf"
    slides = ROOT / "Folio-GroupB-Team4-Slides.pdf"
    pptx = ROOT / "Folio-GroupB-Team4-Slides.pptx"
    build_report(report)
    from build_slides import build_pptx, build_slide_pdf
    build_pptx(pptx)
    build_slide_pdf(slides)
    for path in (report, slides, pptx):
        print(path, path.stat().st_size)


if __name__ == "__main__":
    main()
