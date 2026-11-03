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
from reportlab.platypus import (
    BaseDocTemplate,
    CondPageBreak,
    Frame,
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
    ("2452279", "李灿文", "Li Canwen"),
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
    story.append(P("The brief asks for one English report and one English slide deck, in this order: members, feature list, user manual, architecture including chunking and indexing, then any further notes. The accompanying ZIP keeps the source tree in one folder and these two PDFs in another. Canvas asks for that ZIP before 11:00pm on 3 November 2026. The brief does not name a time zone. The team is working in Asia/Shanghai.", "caption"))

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

    story.append(P("5. Other notes"))
    story.append(P("5.1 What this revision was run against", "h2"))
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

    story.append(P("5.2 Limits worth saying in the room", "h2"))
    story.append(bullets([
        "BM25 can miss a paraphrase that shares no terms with the slide. It can also rank a chunk that shares terms and does not answer the question. The citation shows where the terms matched. It is not a proof that the sentence is right.",
        "The thinking block is visible while a reply streams. The finished card and the stopped card do not keep it.",
        "Practice waits for valid JSON. A bad shape is a failure with the raw reply, not three invented questions.",
        "The JUnit check is syntactic. Run the project’s test command before treating the file as a passing test.",
        "Commit generation does not promise that three candidates come back. It asks for three. The provider decides how many it returns.",
    ]))
    story.append(P("5.3 Build", "h2"))
    story.append(P("The plugin is built with the IntelliJ Platform Gradle Plugin 2.19.0, JDK 25, and IntelliJ IDEA 2026.2.4. sinceBuild is 262. Tika is 4.2.0. Run ./gradlew runIde from the source tree to open the sandbox. The plugin id is cst3492.folio and the version is 0.1.0."))
    story.append(P("5.4 Twenty minutes", "h2"))
    story.append(P("The slides leave most of the slot for the IDE. A workable split is about six minutes of slides and fourteen minutes in Folio: the nine files, one streamed answer with a citation opened, one drill with a single explanation, one stopped generation, then settings and the new-chat confirmation. Test writing and commit fill need a Java source and a staged Git repository. Show them if that project is open. Do not claim them from the retrieval project used above."))

    doc.build(story)


# --- slides -----------------------------------------------------------------

W, H = Inches(13.333), Inches(7.5)
C_NAVY = RGBColor(0x0E, 0x3A, 0x5D)
C_INK = RGBColor(0x1C, 0x24, 0x28)
C_MUTED = RGBColor(0x5C, 0x6B, 0x73)
C_GOLD = RGBColor(0x8C, 0x6A, 0x32)
C_PAPER = RGBColor(0xF7, 0xF4, 0xEE)
C_WHITE = RGBColor(0xFF, 0xFD, 0xF9)


def _run(paragraph, text, size, bold=False, color=C_INK, font="Calibri"):
    run = paragraph.add_run()
    run.text = text
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = font
    rpr = run._r.get_or_add_rPr()
    for tag in ("a:ea", "a:cs"):
        node = rpr.find(qn(tag))
        if node is not None:
            rpr.remove(node)
    for tag in ("a:ea", "a:cs"):
        el = rpr.makeelement(qn(tag))
        el.set("typeface", "Microsoft YaHei")
        rpr.append(el)
    return run


def _box(slide, l, t, w, h, fill):
    shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, l, t, w, h)
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    shape.line.fill.background()
    shape.shadow.inherit = False
    return shape


def _text(slide, l, t, w, h, lines, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    box = slide.shapes.add_textbox(l, t, w, h)
    frame = box.text_frame
    frame.word_wrap = True
    frame.auto_size = None
    frame.margin_left = Emu(0)
    frame.margin_right = Emu(0)
    frame.margin_top = Emu(0)
    frame.margin_bottom = Emu(0)
    try:
        frame._txBody.bodyPr.set("anchor", {MSO_ANCHOR.TOP: "t", MSO_ANCHOR.MIDDLE: "ctr", MSO_ANCHOR.BOTTOM: "b"}[anchor])
    except Exception:
        pass
    for i, line in enumerate(lines):
        p = frame.paragraphs[0] if i == 0 else frame.add_paragraph()
        p.alignment = align
        p.space_after = Pt(line.get("after", 6))
        p.space_before = Pt(line.get("before", 0))
        _run(p, line["text"], line.get("size", 18), line.get("bold", False), line.get("color", C_INK), line.get("font", "Calibri"))
    return box


def _chrome(slide, number, total):
    _box(slide, 0, 0, Inches(0.12), H, C_GOLD)
    _box(slide, 0, Inches(7.18), W, Inches(0.32), C_NAVY)
    _text(slide, Inches(0.38), Inches(7.2), Inches(8), Inches(0.26),
          [{"text": "Folio   ·   CST3492 Group B   ·   Team 4", "size": 11, "color": C_WHITE}])
    _text(slide, Inches(11.4), Inches(7.2), Inches(1.6), Inches(0.26),
          [{"text": f"{number}  /  {total}", "size": 11, "color": C_WHITE}], align=PP_ALIGN.RIGHT)


def _notes(slide, text):
    slide.notes_slide.notes_text_frame.text = text


def build_pptx(path: Path) -> None:
    prs = Presentation()
    prs.slide_width = W
    prs.slide_height = H
    prs.core_properties.title = "Folio — CST3492 Group B, Team 4"
    prs.core_properties.author = "Luo Zhen, Li Canwen, Fan Yu, Meng Shengyu"
    blank = prs.slide_layouts[6]
    total = 14

    def new(n):
        slide = prs.slides.add_slide(blank)
        _box(slide, 0, 0, W, H, C_PAPER)
        _chrome(slide, n, total)
        return slide

    # 1 title
    s = prs.slides.add_slide(blank)
    _box(s, 0, 0, W, H, C_NAVY)
    _box(s, 0, 0, Inches(0.16), H, C_GOLD)
    _text(s, Inches(0.7), Inches(1.15), Inches(11), Inches(0.4),
          [{"text": "CST3492    ·    GROUP B    ·    TEAM 4", "size": 16, "bold": True, "color": RGBColor(0xD7, 0xC4, 0xA1)}])
    _text(s, Inches(0.7), Inches(1.7), Inches(11), Inches(1.2),
          [{"text": "Folio", "size": 60, "bold": True, "color": C_WHITE}])
    _text(s, Inches(0.7), Inches(3.15), Inches(10.5), Inches(1.1),
          [{"text": "Answers from course files, with the page or slide attached,", "size": 22, "color": RGBColor(0xE6, 0xED, 0xF2)},
           {"text": "plus three actions that stay inside IntelliJ.", "size": 22, "color": RGBColor(0xE6, 0xED, 0xF2)}])
    _text(s, Inches(0.7), Inches(5.5), Inches(10), Inches(1.2),
          [{"text": "罗臻    李灿文    范羽    孟圣雨", "size": 18, "color": C_WHITE},
           {"text": "4 November 2026    ·    20 minutes", "size": 16, "color": RGBColor(0xE6, 0xED, 0xF2)}])
    _notes(s, "Open on this slide. Say the team number and that Group B is retrieval plus three IDE actions. Do not start the IDE yet. About 30 seconds.")

    # 2 team
    s = new(2)
    _text(s, Inches(0.45), Inches(0.32), Inches(12), Inches(0.7),
          [{"text": "Team 4", "size": 32, "bold": True, "color": C_NAVY}])
    rows = [
        ("2451989", "罗臻", "Luo Zhen"),
        ("2452279", "李灿文", "Li Canwen"),
        ("2452750", "范羽", "Fan Yu"),
        ("2452272", "孟圣雨", "Meng Shengyu"),
    ]
    top = 1.35
    for i, (sid, name, roman) in enumerate(rows):
        y = Inches(top + i * 1.25)
        _box(s, Inches(0.45), y, Inches(12.3), Inches(1.05), C_WHITE)
        _box(s, Inches(0.45), y, Inches(0.08), Inches(1.05), C_GOLD)
        _text(s, Inches(0.8), y + Inches(0.18), Inches(3.2), Inches(0.7),
              [{"text": sid, "size": 22, "bold": True, "color": C_NAVY}])
        _text(s, Inches(4.2), y + Inches(0.14), Inches(4), Inches(0.75),
              [{"text": name, "size": 28, "bold": True, "color": C_INK}])
        _text(s, Inches(8.4), y + Inches(0.24), Inches(4), Inches(0.6),
              [{"text": roman, "size": 20, "color": C_MUTED}])
    _notes(s, "Read the four names once. Twenty seconds. Move on.")

    # 3 brief
    s = new(3)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.6),
          [{"text": "What Group B has to show", "size": 30, "bold": True, "color": C_NAVY}])
    cards = [
        ("F0", "Course retrieval", "Cut the lectures into chunks.\nIndex them. Retrieve the best ones.\nCite the file and the page or slide."),
        ("F1", "Three IDE actions", "A course drill.\nA JUnit preview, written only on purpose.\nCommit messages from the staged diff."),
        ("Not ours", "Group C", "Rewriting source from an instruction.\nThe built-in diff viewer.\nFolio does not do either."),
    ]
    for i, (k, title, body) in enumerate(cards):
        x = Inches(0.45 + i * 4.2)
        _box(s, x, Inches(1.35), Inches(3.95), Inches(5.2), C_WHITE)
        _box(s, x, Inches(1.35), Inches(3.95), Inches(0.12), C_NAVY if i < 2 else C_GOLD)
        _text(s, x + Inches(0.25), Inches(1.7), Inches(3.4), Inches(0.4),
              [{"text": k.upper(), "size": 13, "bold": True, "color": C_GOLD}])
        _text(s, x + Inches(0.25), Inches(2.15), Inches(3.4), Inches(1.1),
              [{"text": title, "size": 24, "bold": True, "color": C_NAVY}])
        _text(s, x + Inches(0.25), Inches(3.4), Inches(3.4), Inches(2.6),
              [{"text": line, "size": 16, "color": C_INK, "after": 8} for line in body.split("\n")])
    _notes(s, "One minute. F0 is the citation demo. F1 is drill, tests, and commit messages. Say plainly that code rewriting is Group C.")

    # 4 this version
    s = new(4)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.6),
          [{"text": "This version", "size": 30, "bold": True, "color": C_NAVY}])
    facts = [
        ("Plugin", "cst3492.folio  0.1.0"),
        ("Host", "IntelliJ IDEA 2026.2.4, tool window on the right"),
        ("Revision", "16ac42b"),
        ("Model", "DeepSeek chat completions, deepseek-flash"),
        ("Retrieval", "Local BM25. No embeddings."),
        ("Window", "JCEF page. Java owns the library and the writes."),
    ]
    for i, (k, v) in enumerate(facts):
        y = Inches(1.2 + i * 0.9)
        _text(s, Inches(0.5), y, Inches(2.4), Inches(0.6),
              [{"text": k, "size": 18, "bold": True, "color": C_GOLD}])
        _text(s, Inches(3.1), y, Inches(9.5), Inches(0.6),
              [{"text": v, "size": 22, "color": C_INK}])
    _notes(s, "Ten seconds of orientation. The demo will be this build, not a sketch.")

    # 5 feature map
    s = new(5)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "Feature list", "size": 30, "bold": True, "color": C_NAVY}])
    left = [
        "Import PDF, PPTX, and text",
        "Page, slide, or section, then 700 / 100",
        "BM25, at most four chunks",
        "Ask in the sidebar or from a selection",
        "Answer text streams; reasoning stays apart",
        "Citations resolve after the reply",
    ]
    right = [
        "练习 — three exercises, separate explanations",
        "生成测试 — preview, then 写入文件",
        "提交信息 — candidates from the staged diff",
        "No git commit, no change to the index",
        "No key: retrieval still runs, no HTTP call",
        "New chat asks before it clears history",
    ]
    _box(s, Inches(0.4), Inches(1.15), Inches(6.15), Inches(5.7), C_WHITE)
    _box(s, Inches(6.75), Inches(1.15), Inches(6.15), Inches(5.7), C_WHITE)
    _text(s, Inches(0.65), Inches(1.3), Inches(5.6), Inches(0.45),
          [{"text": "F0  ·  retrieval", "size": 16, "bold": True, "color": C_NAVY}])
    _text(s, Inches(7.0), Inches(1.3), Inches(5.6), Inches(0.45),
          [{"text": "F1  ·  three actions", "size": 16, "bold": True, "color": C_NAVY}])
    _text(s, Inches(0.65), Inches(1.9), Inches(5.6), Inches(4.6),
          [{"text": "·   " + line, "size": 16, "color": C_INK, "after": 10} for line in left])
    _text(s, Inches(7.0), Inches(1.9), Inches(5.6), Inches(4.6),
          [{"text": "·   " + line, "size": 16, "color": C_INK, "after": 10} for line in right])
    _notes(s, "Walk the left column, then the right. Point at 写入文件 and say the file appears only after that click. One minute.")

    # 6 manual
    s = new(6)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "Using it", "size": 30, "bold": True, "color": C_NAVY}])
    steps = [
        ("1", "课件", "Import the lecture files. No key required."),
        ("2", "设置", "Save the address, the model, and the key."),
        ("3", "问答", "Ask. Enter sends. The round button stops."),
        ("4", "引用", "Open a blue source title. Read the page."),
        ("5", "模式", "练习, 生成测试, 提交信息 keep their own drafts."),
    ]
    for i, (n, label, detail) in enumerate(steps):
        y = Inches(1.15 + i * 1.1)
        _box(s, Inches(0.45), y, Inches(0.7), Inches(0.7), C_NAVY)
        _text(s, Inches(0.45), y + Inches(0.12), Inches(0.7), Inches(0.5),
              [{"text": n, "size": 22, "bold": True, "color": C_WHITE}], align=PP_ALIGN.CENTER)
        _text(s, Inches(1.4), y + Inches(0.02), Inches(3.2), Inches(0.7),
              [{"text": label, "size": 26, "bold": True, "color": C_NAVY}])
        _text(s, Inches(4.8), y + Inches(0.12), Inches(7.8), Inches(0.55),
              [{"text": detail, "size": 20, "color": C_INK}])
    _notes(s, "This is the user manual in one slide. The IDE demo follows these five steps. Mention Enter versus Shift+Enter only if someone asks.")

    # 7 architecture
    s = new(7)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "From a file to a citation", "size": 30, "bold": True, "color": C_NAVY}])
    nodes = [
        (0.4, "Files", "PDF, PPTX, text"),
        (2.9, "Tika 4.2.0", "page or slide"),
        (5.4, "Chunks", "700 / 100"),
        (7.9, "BM25", "top 4"),
        (10.4, "Sidebar", "file and page"),
    ]
    for x, title, sub in nodes:
        _box(s, Inches(x), Inches(2.3), Inches(2.3), Inches(1.7), C_WHITE)
        _box(s, Inches(x), Inches(2.3), Inches(2.3), Inches(0.08), C_NAVY)
        _text(s, Inches(x + 0.12), Inches(2.5), Inches(2.05), Inches(0.7),
              [{"text": title, "size": 18, "bold": True, "color": C_NAVY}])
        _text(s, Inches(x + 0.12), Inches(3.2), Inches(2.05), Inches(0.5),
              [{"text": sub, "size": 14, "color": C_MUTED}])
    _text(s, Inches(0.45), Inches(4.4), Inches(12.3), Inches(2.3),
          [{"text": "The question and the selection choose the chunks.", "size": 18, "color": C_INK, "after": 4},
           {"text": "History is quoted for the model. It is not citation evidence.", "size": 18, "color": C_INK, "after": 4},
           {"text": "The model streams. Citation numbers are mapped after the reply finishes.", "size": 18, "color": C_INK}])
    _notes(s, "Trace left to right with a finger on the slide, then say the two sentences at the bottom. Forty-five seconds. The next two slides unpack chunks and BM25.")

    # 8 chunks
    s = new(8)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "How a lecture becomes chunks", "size": 30, "bold": True, "color": C_NAVY}])
    blocks = [
        ("PDF", "One unit per original page, including a blank page’s number."),
        ("PPTX", "One unit per slide: groups, tables, and notes. Master text is dropped."),
        ("Text", "TXT, Markdown, and DOCX are sections. No invented page number."),
        ("Then", "Cut at 700 Java characters, overlap 100, keep the original page or slide."),
    ]
    for i, (title, body) in enumerate(blocks):
        col = i % 2
        row = i // 2
        x = Inches(0.45 + col * 6.4)
        y = Inches(1.3 + row * 2.6)
        _box(s, x, y, Inches(6.1), Inches(2.3), C_WHITE)
        _text(s, x + Inches(0.3), y + Inches(0.25), Inches(5.5), Inches(0.5),
              [{"text": title, "size": 22, "bold": True, "color": C_NAVY}])
        _text(s, x + Inches(0.3), y + Inches(0.9), Inches(5.5), Inches(1.1),
              [{"text": body, "size": 16, "color": C_INK}])
    _notes(s, "Emphasize original page numbers and no slide-master text. The 700/100 split is so one long page can still cite the same page twice.")

    # 9 index
    s = new(9)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "Index and retrieval", "size": 30, "bold": True, "color": C_NAVY}])
    points = [
        "BM25, k1 = 1.2, b = 0.75. No vector model.",
        "English words, plus overlapping bigrams for Chinese, Japanese, and Korean.",
        "A hit must cover at least half of the question’s coverage groups.",
        "Scores under 25% of the best score are dropped. At most four chunks remain.",
        "Chinese bigrams that are not in an English deck are ignored when the question also has Latin terms that do occur.",
    ]
    _text(s, Inches(0.55), Inches(1.25), Inches(12.2), Inches(5.4),
          [{"text": "·   " + line, "size": 20, "color": C_INK, "after": 14} for line in points])
    _notes(s, "The last bullet is the fix in 16ac42b. A Chinese question about JUnit used to miss the English slide because the Chinese bigrams could never be covered. Say that, then go to the demo result on the later slide.")

    # 10 stream
    s = new(10)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "Streaming, then the citation", "size": 30, "bold": True, "color": C_NAVY}])
    cols = [
        ("Ask", "Answer tokens append as they arrive.\nReasoning stays in 思考.\n停止生成 keeps the answer text already shown."),
        ("After", "Prose [1] and [2] map onto retrieved chunks.\nCode, calls, and indexes are left alone.\nThe blue title opens the excerpt."),
        ("Drill", "The same stream is read.\nThe panel keeps the phase only.\nThree exercises appear when the JSON parses."),
    ]
    for i, (title, body) in enumerate(cols):
        x = Inches(0.4 + i * 4.25)
        _box(s, x, Inches(1.3), Inches(4.05), Inches(5.4), C_WHITE)
        _text(s, x + Inches(0.25), Inches(1.5), Inches(3.5), Inches(0.6),
              [{"text": title, "size": 22, "bold": True, "color": C_NAVY}])
        _text(s, x + Inches(0.25), Inches(2.3), Inches(3.55), Inches(3.8),
              [{"text": line, "size": 16, "color": C_INK, "after": 10} for line in body.split("\n")])
    _notes(s, "Be honest. Ask streams. Drill does not show tokens. If you demo streaming, use 问答 and let it finish a sentence before you stop a second question.")

    # 11 actions
    s = new(11)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "The three actions", "size": 30, "bold": True, "color": C_NAVY}])
    acts = [
        ("练习", "Three questions about the selection or the topic. Each explanation opens on its own. The editor does not change."),
        ("生成测试", "A scenario first. Then a preview. 写入文件 creates the test. Syntax and JUnit annotations are checked. The test is not run."),
        ("提交信息", "Staged diff only. Candidates can be copied or filled into the commit box. Folio never commits and never changes the index."),
    ]
    for i, (title, body) in enumerate(acts):
        y = Inches(1.2 + i * 1.85)
        _box(s, Inches(0.45), y, Inches(12.4), Inches(1.7), C_WHITE)
        _text(s, Inches(0.7), y + Inches(0.28), Inches(3.2), Inches(1.1),
              [{"text": title, "size": 26, "bold": True, "color": C_NAVY}])
        _text(s, Inches(4.1), y + Inches(0.32), Inches(8.4), Inches(1.15),
              [{"text": body, "size": 16, "color": C_INK}])
    _notes(s, "Ninety seconds. Stress the two refusals: no automatic test file, no automatic commit.")

    # 12 evidence
    s = new(12)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "Run on the nine lectures", "size": 30, "bold": True, "color": C_NAVY}])
    _text(s, Inches(0.5), Inches(1.15), Inches(12), Inches(0.4),
          [{"text": "Sandbox, 3 November 2026, revision 16ac42b, deepseek-flash.", "size": 16, "color": C_MUTED}])
    ev = [
        "Nine PDFs, Lec-0 through Lec-5, including Lec-3.1 to Lec-3.4.",
        "“JUnit 5 的测试方法通常用哪个注解？” cited Lec-3.4, pages 3 and 2. The answer was @Test.",
        "The page-3 excerpt opened from the citation title.",
        "“比较 checked 与 unchecked exception” produced three exercises. One explanation opened.",
        "A generics question showed 思考, then 已停止生成.",
        "Drafts stayed with 问答, 生成测试, and 提交信息. New chat asked, and cancel kept the history.",
    ]
    _text(s, Inches(0.5), Inches(1.7), Inches(12.3), Inches(5.1),
          [{"text": "·   " + line, "size": 18, "color": C_INK, "after": 8} for line in ev])
    _notes(s, "These are the checks from the live IDE, not a claim that every action was exercised. Say that test write and commit fill were not run in that project because it had no staged Git repository.")

    # 13 limits
    s = new(13)
    _text(s, Inches(0.45), Inches(0.28), Inches(12), Inches(0.55),
          [{"text": "What we will not claim", "size": 30, "bold": True, "color": C_NAVY}])
    limits = [
        ("Retrieval", "Shared words are not the same thing as a correct answer."),
        ("Thinking", "Visible while streaming. Gone from the finished and the stopped card."),
        ("Practice", "Waits for three parsed exercises. It does not stream tokens."),
        ("Tests", "PSI and JUnit annotations only. Not compiled, not executed."),
        ("Commits", "Asks for three messages. Does not commit. Refuses a stale index."),
    ]
    for i, (k, v) in enumerate(limits):
        y = Inches(1.15 + i * 1.1)
        _text(s, Inches(0.5), y, Inches(2.6), Inches(0.7),
              [{"text": k, "size": 20, "bold": True, "color": C_GOLD}])
        _text(s, Inches(3.3), y, Inches(9.4), Inches(0.8),
              [{"text": v, "size": 20, "color": C_INK}])
    _notes(s, "Say this before the demo so a failed paraphrase or a quiet drill does not look like a surprise. Thirty seconds.")

    # 14 demo
    s = new(14)
    _text(s, Inches(0.45), Inches(0.22), Inches(12), Inches(0.5),
          [{"text": "Demonstration", "size": 30, "bold": True, "color": C_NAVY}])
    plan = [
        ("0:00", "This slide, then the tool window"),
        ("2:00", "课件 — the nine PDFs"),
        ("4:00", "问答 — let one answer stream, then open a citation"),
        ("9:00", "练习 — three exercises, open one 查看解析"),
        ("12:00", "Stop a second question on 正在思考"),
        ("14:00", "设置, then 新对话 and 取消"),
        ("16:00", "If a Git project is open: 提交信息. If Java is open: 生成测试"),
        ("19:00", "Close on the limits"),
    ]
    for i, (t, line) in enumerate(plan):
        y = Inches(0.9 + i * 0.75)
        _text(s, Inches(0.5), y, Inches(1.6), Inches(0.5),
              [{"text": t, "size": 18, "bold": True, "color": C_GOLD}])
        _text(s, Inches(2.3), y, Inches(10.3), Inches(0.5),
              [{"text": line, "size": 18, "color": C_INK}])
    _notes(s, "Leave this slide up only while you switch to IntelliJ. Do not read every timestamp aloud. Hit 课件, one full streamed answer, one citation, one drill, one stop, settings, cancel new chat. Tests and commits only if the right project is open.")

    prs.save(str(path))


def draw_mixed(c, x, y, text, size=14, color=INK, font_latin="Inter"):
    """Draw a line that may mix Latin letters with Chinese."""
    c.setFillColor(color)
    cursor = x
    buf = ""
    mode = None

    def flush(current, chunk):
        nonlocal cursor
        if not chunk:
            return
        font = "CJK" if current == "cjk" else font_latin
        c.setFont(font, size)
        c.drawString(cursor, y, chunk)
        cursor += c.stringWidth(chunk, font, size)

    for ch in text:
        kind = "cjk" if ord(ch) > 127 else "lat"
        if mode is None:
            mode = kind
        if kind != mode:
            flush(mode, buf)
            buf = ch
            mode = kind
        else:
            buf += ch
    flush(mode, buf)


def build_slide_pdf(path: Path) -> None:
    """Widescreen PDF of the same 14 slides, for the required slide PDF."""
    from reportlab.pdfgen import canvas as pdfcanvas

    pages = [
        ("title",),
    ]
    c = pdfcanvas.Canvas(str(path), pagesize=(960, 540))
    c.setTitle("Folio — CST3492 Group B, Team 4")
    c.setAuthor("Luo Zhen, Li Canwen, Fan Yu, Meng Shengyu")

    def paper():
        c.setFillColor(PAPER)
        c.rect(0, 0, 960, 540, fill=1, stroke=0)
        c.setFillColor(GOLD)
        c.rect(0, 0, 8, 540, fill=1, stroke=0)
        c.setFillColor(NAVY)
        c.rect(0, 0, 960, 22, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont("Inter", 9)
        c.drawString(24, 7, "Folio    ·    CST3492 Group B    ·    Team 4")

    def footer_num(n):
        c.setFillColor(white)
        c.setFont("Inter", 9)
        c.drawRightString(936, 7, f"{n}  /  14")

    def h1(text, y=500):
        c.setFillColor(NAVY)
        c.setFont("Inter-Bold", 26)
        c.drawString(28, y, text)

    # 1
    c.setFillColor(NAVY)
    c.rect(0, 0, 960, 540, fill=1, stroke=0)
    c.setFillColor(GOLD)
    c.rect(0, 0, 10, 540, fill=1, stroke=0)
    c.setFillColor(HexColor("#D7C4A1"))
    c.setFont("Inter-Semi", 12)
    c.drawString(48, 430, "CST3492     ·     GROUP B     ·     TEAM 4")
    c.setFillColor(white)
    c.setFont("Inter-Bold", 54)
    c.drawString(48, 360, "Folio")
    c.setFont("Inter", 16)
    c.setFillColor(HexColor("#E6EDF2"))
    c.drawString(48, 310, "Answers from course files, with the page or slide attached,")
    c.drawString(48, 288, "plus three actions that stay inside IntelliJ.")
    c.setFillColor(white)
    c.setFont("CJK", 16)
    c.drawString(48, 150, "罗臻     李灿文     范羽     孟圣雨")
    c.setFont("Inter", 13)
    c.setFillColor(HexColor("#E6EDF2"))
    c.drawString(48, 120, "4 November 2026      ·      20 minutes")
    c.showPage()

    def bullets_at(items, y, size=15, gap=28, color=INK):
        c.setFillColor(color)
        c.setFont("Inter", size)
        for item in items:
            c.drawString(36, y, "·")
            c.drawString(54, y, item)
            y -= gap
        return y

    # 2
    paper(); footer_num(2); h1("Team 4")
    for i, (sid, name, roman) in enumerate(TEAM):
        y = 400 - i * 78
        c.setFillColor(CARD)
        c.roundRect(28, y - 18, 900, 64, 4, fill=1, stroke=0)
        c.setFillColor(GOLD)
        c.rect(28, y - 18, 5, 64, fill=1, stroke=0)
        c.setFillColor(NAVY)
        c.setFont("Inter-Bold", 16)
        c.drawString(52, y + 8, sid)
        c.setFillColor(INK)
        c.setFont("CJK", 20)
        c.drawString(280, y + 6, name)
        c.setFillColor(MUTED)
        c.setFont("Inter", 16)
        c.drawString(520, y + 8, roman)
    c.showPage()

    # 3
    paper(); footer_num(3); h1("What Group B has to show")
    cards = [
        ("F0", "Course retrieval", ["Cut the lectures into chunks.", "Index them. Retrieve the best.", "Cite the file and the page."]),
        ("F1", "Three IDE actions", ["A course drill.", "A JUnit preview, written on purpose.", "Commit messages from staging."]),
        ("NOT OURS", "Group C", ["No instruction-to-edit.", "No built-in diff editing.", "That scope is Group C."]),
    ]
    for i, (k, title, lines) in enumerate(cards):
        x = 28 + i * 308
        c.setFillColor(CARD)
        c.roundRect(x, 70, 292, 360, 4, fill=1, stroke=0)
        c.setFillColor(NAVY if i < 2 else GOLD)
        c.rect(x, 418, 292, 12, fill=1, stroke=0)
        c.setFillColor(GOLD)
        c.setFont("Inter-Semi", 11)
        c.drawString(x + 16, 380, k)
        c.setFillColor(NAVY)
        c.setFont("Inter-Bold", 18)
        c.drawString(x + 16, 348, title)
        c.setFillColor(INK)
        c.setFont("Inter", 13)
        yy = 300
        for line in lines:
            c.drawString(x + 16, yy, line)
            yy -= 28
    c.showPage()

    # 4
    paper(); footer_num(4); h1("This version")
    facts = [
        ("Plugin", "cst3492.folio  0.1.0"),
        ("Host", "IntelliJ IDEA 2026.2.4, tool window on the right"),
        ("Revision", "16ac42b"),
        ("Model", "DeepSeek chat completions, deepseek-flash"),
        ("Retrieval", "Local BM25. No embeddings."),
        ("Window", "JCEF page. Java owns the library and the writes."),
    ]
    for i, (k, v) in enumerate(facts):
        y = 430 - i * 58
        c.setFillColor(GOLD)
        c.setFont("Inter-Semi", 14)
        c.drawString(36, y, k)
        c.setFillColor(INK)
        c.setFont("Inter", 16)
        c.drawString(180, y, v)
    c.showPage()

    # 5
    paper(); footer_num(5); h1("Feature list", 500)
    c.setFillColor(CARD)
    c.roundRect(24, 36, 446, 420, 4, fill=1, stroke=0)
    c.roundRect(486, 36, 446, 420, 4, fill=1, stroke=0)
    c.setFillColor(NAVY)
    c.setFont("Inter-Bold", 14)
    c.drawString(40, 420, "F0   ·   retrieval")
    c.drawString(506, 420, "F1   ·   three actions")
    left = ["Import PDF, PPTX, and text", "Page, slide, or section, then 700 / 100", "BM25, at most four chunks", "Ask in the sidebar or from a selection", "Answer text streams; reasoning stays apart", "Citations resolve after the reply"]
    right = ["Practice: three exercises", "Tests: preview, then write the file", "Commits: candidates from the staged diff", "No git commit, no change to the index", "No key: retrieval still runs, no HTTP", "New chat asks before it clears history"]
    c.setFillColor(INK)
    c.setFont("Inter", 13)
    for i, line in enumerate(left):
        c.drawString(44, 380 - i * 48, "·   " + line)
    for i, line in enumerate(right):
        c.drawString(510, 380 - i * 48, "·   " + line)
    c.showPage()

    # 6
    paper(); footer_num(6); h1("Using it")
    steps = [
        ("1", "Import", "课件. No key required."),
        ("2", "Connect", "设置. Address, model, and key."),
        ("3", "Ask", "问答. Enter sends. The round button stops."),
        ("4", "Cite", "Open a source title. Read the page."),
        ("5", "Switch", "练习, 生成测试, 提交信息 keep their drafts."),
    ]
    for i, (n, label, detail) in enumerate(steps):
        y = 420 - i * 72
        c.setFillColor(NAVY)
        c.circle(52, y + 6, 16, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont("Inter-Bold", 14)
        c.drawCentredString(52, y, n)
        c.setFillColor(NAVY)
        c.setFont("Inter-Bold", 16)
        c.drawString(86, y, label)
        draw_mixed(c, 250, y, detail, size=14, color=INK)
    c.showPage()

    # 7
    paper(); footer_num(7); h1("From a file to a citation")
    nodes = [("Files", "PDF, PPTX, text"), ("Tika 4.2.0", "page or slide"), ("Chunks", "700 / 100"), ("BM25", "top 4"), ("Sidebar", "file and page")]
    for i, (title, sub) in enumerate(nodes):
        x = 24 + i * 188
        c.setFillColor(CARD)
        c.roundRect(x, 250, 172, 90, 4, fill=1, stroke=0)
        c.setFillColor(NAVY)
        c.rect(x, 332, 172, 8, fill=1, stroke=0)
        c.setFont("Inter-Bold", 13)
        c.drawString(x + 12, 300, title)
        c.setFillColor(MUTED)
        c.setFont("Inter", 11)
        c.drawString(x + 12, 276, sub)
    c.setFillColor(INK)
    c.setFont("Inter", 14)
    c.drawString(36, 180, "The question and the selection choose the chunks.")
    c.drawString(36, 152, "History is quoted for the model. It is not citation evidence.")
    c.drawString(36, 124, "The model streams. Citation numbers are mapped after the reply finishes.")
    c.showPage()

    # 8
    paper(); footer_num(8); h1("How a lecture becomes chunks")
    blocks = [
        ("PDF", "One unit per original page, including a blank page’s number."),
        ("PPTX", "One unit per slide. Master text is dropped. Notes stay."),
        ("Text", "TXT, Markdown, and DOCX are sections. No invented page."),
        ("Then", "Cut at 700 Java characters, overlap 100, keep the page."),
    ]
    for i, (title, body) in enumerate(blocks):
        col, row = i % 2, i // 2
        x, y = 28 + col * 468, 300 - row * 180
        c.setFillColor(CARD)
        c.roundRect(x, y, 444, 150, 4, fill=1, stroke=0)
        c.setFillColor(NAVY)
        c.setFont("Inter-Bold", 18)
        c.drawString(x + 18, y + 100, title)
        c.setFillColor(INK)
        c.setFont("Inter", 13)
        c.drawString(x + 18, y + 64, body)
    c.showPage()

    # 9
    paper(); footer_num(9); h1("Index and retrieval")
    lines = [
        "BM25, k1 = 1.2, b = 0.75. No vector model.",
        "English words, plus bigrams for Chinese, Japanese, and Korean.",
        "A hit must cover at least half of the question’s coverage groups.",
        "Scores under 25% of the best score are dropped. At most four remain.",
        "Unattested Chinese groups drop out when Latin terms are actually in the index.",
    ]
    c.setFillColor(INK)
    c.setFont("Inter", 15)
    y = 420
    for line in lines:
        c.drawString(40, y, "·")
        # wrap manually if long
        c.drawString(62, y, line)
        y -= 52
    c.showPage()

    # 10
    paper(); footer_num(10); h1("Streaming, then the citation")
    cols = [
        ("Ask", ["Answer tokens append as they arrive.", "Reasoning stays in its own block.", "Stop keeps the answer text."]),
        ("After", ["Prose [1] maps onto a retrieved chunk.", "Code and calls stay literal.", "The source title opens the excerpt."]),
        ("Drill", ["The same stream is read.", "The panel shows the phase only.", "Exercises appear when JSON parses."]),
    ]
    for i, (title, lines) in enumerate(cols):
        x = 24 + i * 312
        c.setFillColor(CARD)
        c.roundRect(x, 70, 296, 360, 4, fill=1, stroke=0)
        c.setFillColor(NAVY)
        c.setFont("Inter-Bold", 18)
        c.drawString(x + 16, 390, title)
        c.setFillColor(INK)
        c.setFont("Inter", 13)
        yy = 340
        for line in lines:
            c.drawString(x + 16, yy, line)
            yy -= 48
    c.showPage()

    # 11
    paper(); footer_num(11); h1("The three actions")
    acts = [
        ("Practice", "Three questions. Each explanation opens alone. The editor does not change."),
        ("Tests", "Scenario, then preview, then an explicit write. Syntax only. The test is not run."),
        ("Commits", "Staged diff only. Copy or fill one candidate. Folio never commits."),
    ]
    for i, (title, body) in enumerate(acts):
        y = 360 - i * 120
        c.setFillColor(CARD)
        c.roundRect(28, y, 904, 100, 4, fill=1, stroke=0)
        c.setFillColor(NAVY)
        c.setFont("Inter-Bold", 16)
        c.drawString(48, y + 58, title)
        c.setFillColor(INK)
        c.setFont("Inter", 13)
        c.drawString(220, y + 40, body)
    c.showPage()

    # 12
    paper(); footer_num(12); h1("Run on the nine lectures", 500)
    c.setFillColor(MUTED)
    c.setFont("Inter", 12)
    c.drawString(36, 458, "Sandbox, 3 November 2026, revision 16ac42b, deepseek-flash.")
    lines = [
        "Nine PDFs, Lec-0 through Lec-5, including Lec-3.1 to Lec-3.4.",
        "The JUnit annotation question cited Lec-3.4, pages 3 and 2.",
        "The answer named @Test. The page-3 excerpt opened from the title.",
        "The exception drill produced three exercises. One explanation opened.",
        "A generics question showed thinking, then stopped cleanly.",
        "Drafts survived mode switches. Cancel kept the conversation.",
    ]
    c.setFillColor(INK)
    c.setFont("Inter", 14)
    y = 410
    for line in lines:
        c.drawString(40, y, "·    " + line)
        y -= 48
    c.showPage()

    # 13
    paper(); footer_num(13); h1("What we will not claim")
    limits = [
        ("Retrieval", "Shared words are not a correct answer."),
        ("Thinking", "Shown while streaming. Not kept on the finished card."),
        ("Practice", "Waits for three exercises. It does not stream tokens."),
        ("Tests", "Annotations and syntax. Not compiled and not executed."),
        ("Commits", "Does not commit. Refuses a stale index."),
    ]
    for i, (k, v) in enumerate(limits):
        y = 420 - i * 68
        c.setFillColor(GOLD)
        c.setFont("Inter-Semi", 15)
        c.drawString(36, y, k)
        c.setFillColor(INK)
        c.setFont("Inter", 15)
        c.drawString(180, y, v)
    c.showPage()

    # 14
    paper(); footer_num(14); h1("Demonstration", 500)
    plan = [
        ("0:00", "Slides, then the tool window"),
        ("2:00", "The nine PDFs"),
        ("4:00", "One streamed answer, then open a citation"),
        ("9:00", "Three exercises, open one explanation"),
        ("12:00", "Stop a second question while it is thinking"),
        ("14:00", "Settings, then new chat and cancel"),
        ("16:00", "Commit messages or a test, if that project is open"),
        ("19:00", "Close on the limits"),
    ]
    c.setFont("Inter", 14)
    for i, (t, line) in enumerate(plan):
        y = 440 - i * 48
        c.setFillColor(GOLD)
        c.setFont("Inter-Semi", 14)
        c.drawString(36, y, t)
        c.setFillColor(INK)
        c.setFont("Inter", 14)
        c.drawString(130, y, line)
    c.save()


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    report = ROOT / "Folio-GroupB-Team4-Report.pdf"
    slides = ROOT / "Folio-GroupB-Team4-Slides.pdf"
    pptx = ROOT / "Folio-GroupB-Team4-Slides.pptx"
    build_report(report)
    build_slide_pdf(slides)
    build_pptx(pptx)
    for path in (report, slides, pptx):
        print(path, path.stat().st_size)


if __name__ == "__main__":
    main()
