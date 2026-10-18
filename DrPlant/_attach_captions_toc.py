# -*- coding: utf-8 -*-
"""
Merge-ready: attach Caption titles with Word SEQ fields, rebuild
LIST OF TABLES / LIST OF FIGURES, refresh front TOC field.
"""
import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, Twips
from docx.text.paragraph import Paragraph

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

ROOT = Path(r"C:\AI-Agriculture-Ecosystem\DrPlant")
DOC_PATH = ROOT / "BaoCaoTienDo_DrPlant_Mobile.docx"
DOC_ALT = ROOT / "BaoCaoTienDo_DrPlant_Mobile_updated.docx"


def set_run_font(run, size=12, bold=False, italic=False):
    run.font.name = "Times New Roman"
    rpr = run._element.get_or_add_rPr()
    rFonts = rpr.get_or_add_rFonts()
    rFonts.set(qn("w:ascii"), "Times New Roman")
    rFonts.set(qn("w:hAnsi"), "Times New Roman")
    rFonts.set(qn("w:eastAsia"), "Times New Roman")
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic


def clear_num_pr(p):
    p_pr = p._p.get_or_add_pPr()
    num_pr = p_pr.find(qn("w:numPr"))
    if num_pr is not None:
        p_pr.remove(num_pr)


def clear_runs(paragraph):
    for child in list(paragraph._p):
        if child.tag.endswith("}r") or child.tag.endswith("}hyperlink"):
            paragraph._p.remove(child)


def add_fld_char(run, kind):
    fc = OxmlElement("w:fldChar")
    fc.set(qn("w:fldCharType"), kind)
    run._r.append(fc)


def add_instr_text(run, instr):
    it = OxmlElement("w:instrText")
    it.set(qn("xml:space"), "preserve")
    it.text = instr
    run._r.append(it)


def add_seq_field(paragraph, seq_name, display_text):
    """Insert { SEQ name \\* ARABIC } with cached display_text."""
    r1 = paragraph.add_run()
    set_run_font(r1, 12, False, True)
    add_fld_char(r1, "begin")

    r2 = paragraph.add_run()
    set_run_font(r2, 12, False, True)
    add_instr_text(r2, f" SEQ {seq_name} \\* ARABIC ")

    r3 = paragraph.add_run()
    set_run_font(r3, 12, False, True)
    add_fld_char(r3, "separate")

    r4 = paragraph.add_run(display_text)
    set_run_font(r4, 12, False, True)

    r5 = paragraph.add_run()
    set_run_font(r5, 12, False, True)
    add_fld_char(r5, "end")


def set_caption_with_seq(paragraph, kind, number, title):
    """
    kind: 'Table' or 'Figure'
    number: display number e.g. '1' or '3.1'
    Builds: Table {SEQ}. Title   or   Figure {SEQ}. Title
    SEQ name is Table/Figure so Word TOC \\c works.
    Cached SEQ result shows the custom number (3.1, 4.2, ...).
    """
    paragraph.style = "Caption"
    clear_num_pr(paragraph)
    clear_runs(paragraph)
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.first_line_indent = Cm(0)
    paragraph.paragraph_format.space_before = Pt(6)
    paragraph.paragraph_format.space_after = Pt(6)

    r0 = paragraph.add_run(f"{kind} ")
    set_run_font(r0, 12, False, True)
    add_seq_field(paragraph, kind, str(number))
    r_rest = paragraph.add_run(f". {title}")
    set_run_font(r_rest, 12, False, True)


def find_heading(doc, text):
    for p in doc.paragraphs:
        if p.text.strip() == text:
            return p
    return None


def remove_following_until_heading(heading_para):
    """Remove paragraphs after a heading until next Heading 1."""
    nxt = heading_para._p.getnext()
    while nxt is not None:
        if nxt.tag != qn("w:p"):
            break
        p = Paragraph(nxt, heading_para._parent)
        if p.style and p.style.name == "Heading 1":
            break
        following = nxt.getnext()
        nxt.getparent().remove(nxt)
        nxt = following


def add_toc_field(paragraph, instr, placeholder="Right-click → Update Field"):
    clear_runs(paragraph)
    clear_num_pr(paragraph)
    r1 = paragraph.add_run()
    set_run_font(r1, 13, False)
    add_fld_char(r1, "begin")
    r2 = paragraph.add_run()
    set_run_font(r2, 13, False)
    add_instr_text(r2, f" {instr} ")
    r3 = paragraph.add_run()
    set_run_font(r3, 13, False)
    add_fld_char(r3, "separate")
    r4 = paragraph.add_run(placeholder)
    set_run_font(r4, 13, False, True)
    r5 = paragraph.add_run()
    set_run_font(r5, 13, False)
    add_fld_char(r5, "end")


def add_static_lof_line(anchor, parent, text):
    elm = OxmlElement("w:p")
    anchor._p.addnext(elm)
    p = Paragraph(elm, parent)
    clear_num_pr(p)
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.15
    # tab with dot leader to page placeholder
    pPr = p._p.get_or_add_pPr()
    tabs = OxmlElement("w:tabs")
    tab = OxmlElement("w:tab")
    tab.set(qn("w:val"), "right")
    tab.set(qn("w:leader"), "dot")
    tab.set(qn("w:pos"), "8500")
    tabs.append(tab)
    pPr.append(tabs)
    set_run_font(p.add_run(text), 13, False)
    return p


# Caption definitions in document order (matched by keyword in existing caption text)
TABLE_DEFS = [
    ("assignments for the progress", "1", "List of assignments for the progress milestone"),
    ("list of abbreviations", "2", "List of abbreviations"),
    ("main functional requirements", "3", "Main functional requirements"),
    ("screens implemented", "4", "Screens implemented in this build"),
    ("progress against planned", "5", "Progress against planned modules"),
]

FIGURE_DEFS = [
    ("data flow from image", "3.1", "Data flow from image capture to result and local history"),
    ("navigation flow", "3.2", "Navigation flow between the main DrPlant screens"),
    ("use case diagram", "3.3", "Use case diagram for the progress build"),
    ("layered architecture", "3.4", "Layered architecture of the DrPlant Android application"),
    ("login screen", "4.1", "Login screen of DrPlant"),
    ("register screen", "4.2", "Register screen of DrPlant"),
    ("camera home screen", "4.3", "Camera home screen of DrPlant"),
    ("diagnosis result screen", "4.4", "Diagnosis result screen of DrPlant"),
    ("history screen", "4.5", "History screen of DrPlant"),
    ("statistics screen", "4.6", "Statistics screen of DrPlant"),
]


def match_def(text, defs):
    t = text.lower()
    for key, num, title in defs:
        if key in t:
            return num, title
    return None


def attach_all_captions(doc):
    table_n = 0
    figure_n = 0
    for p in doc.paragraphs:
        if not (p.style and p.style.name == "Caption"):
            continue
        raw = p.text.strip()
        low = raw.lower()
        if low.startswith("table") or "table " in low[:12]:
            m = match_def(raw, TABLE_DEFS)
            if m:
                num, title = m
            else:
                table_n += 1
                num = str(table_n)
                title = re.sub(r"^(Table|Figure)\s*[\d.]+\s*\.?\s*", "", raw, flags=re.I).strip()
            set_caption_with_seq(p, "Table", num, title)
            table_n = max(table_n, int(str(num).split(".")[0]) if str(num).isdigit() else table_n)
            print("Caption Table", num, title)
        elif "figure" in low:
            m = match_def(raw, FIGURE_DEFS)
            if m:
                num, title = m
            else:
                figure_n += 1
                num = str(figure_n)
                title = re.sub(r"^(Table|Figure)\s*[\d.]+\s*\.?\s*", "", raw, flags=re.I).strip()
            set_caption_with_seq(p, "Figure", num, title)
            print("Caption Figure", num, title)


def rebuild_list_section(doc, heading_text, lines, toc_instr):
    h = find_heading(doc, heading_text)
    if h is None:
        print("Missing heading:", heading_text)
        return
    remove_following_until_heading(h)

    # Static list (readable even before Update Field)
    anchor = h
    for line in lines:
        anchor = add_static_lof_line(anchor, h._parent, line)

    # Auto TOC field paragraph (Word: right-click Update Field)
    elm = OxmlElement("w:p")
    anchor._p.addnext(elm)
    p = Paragraph(elm, h._parent)
    add_toc_field(
        p,
        toc_instr,
        placeholder="(In Word: right-click this line → Update Field to refresh page numbers)",
    )
    print("Rebuilt", heading_text, "entries:", len(lines))


def refresh_main_toc_field(doc):
    """Ensure TABLE OF CONTENTS has a TOC field Word can update."""
    h = find_heading(doc, "TABLE OF CONTENTS")
    if h is None:
        return
    # If following paras are toc 1/2/3 styles, replace block with TOC field
    # Keep existing static TOC if present; append update note once
    nxt = h._p.getnext()
    has_toc_field = False
    while nxt is not None and nxt.tag == qn("w:p"):
        p = Paragraph(nxt, h._parent)
        if "w:fldChar" in p._p.xml and "TOC" in p._p.xml:
            has_toc_field = True
            break
        st = p.style.name if p.style else ""
        if st == "Heading 1" and p.text.strip() != "TABLE OF CONTENTS":
            break
        if st.startswith("toc"):
            nxt = nxt.getnext()
            continue
        break

    if not has_toc_field:
        # Insert TOC field right after heading (Word will expand on update)
        # Keep existing toc lines as fallback; add field at end of toc block
        last = h
        nxt = h._p.getnext()
        while nxt is not None and nxt.tag == qn("w:p"):
            p = Paragraph(nxt, h._parent)
            st = p.style.name if p.style else ""
            if st.startswith("toc"):
                last = p
                nxt = nxt.getnext()
                continue
            break
        elm = OxmlElement("w:p")
        last._p.addnext(elm)
        p = Paragraph(elm, h._parent)
        add_toc_field(
            p,
            'TOC \\o "1-3" \\h \\z \\u',
            placeholder="(In Word: References → Update Table, or right-click → Update Field)",
        )
        print("Inserted main TOC field")


def main():
    # Prefer main; sync from updated if main missing
    if not DOC_PATH.exists() and DOC_ALT.exists():
        DOC_ALT.replace(DOC_PATH)

    doc = Document(str(DOC_PATH))
    attach_all_captions(doc)

    table_lines = [f"Table {n}. {t}" for _, n, t in TABLE_DEFS]
    figure_lines = [f"Figure {n}. {t}" for _, n, t in FIGURE_DEFS]

    rebuild_list_section(
        doc,
        "LIST OF TABLES",
        table_lines,
        'TOC \\h \\z \\c "Table"',
    )
    rebuild_list_section(
        doc,
        "LIST OF FIGURES",
        figure_lines,
        'TOC \\h \\z \\c "Figure"',
    )
    refresh_main_toc_field(doc)

    for target in (DOC_PATH, DOC_ALT):
        try:
            doc.save(str(target))
            print("Saved", target)
        except PermissionError:
            print("Locked:", target.name)


if __name__ == "__main__":
    main()
