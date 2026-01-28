import os
import random
import re
import tempfile
from pathlib import Path

import fitz

import requests

DEFAULT_API_URL = os.getenv("MINERU_API_URL", "http://localhost:18000/file_parse")
_TAG_RE = re.compile(r"<[^>]+>")
_MD_HEADING_RE = re.compile(r"^\s{0,3}#{1,6}\s*", re.MULTILINE)
_MD_IMAGE_RE = re.compile(r"!\[[^\]]*\]\([^\)]*\)")
_MD_INLINE_MATH_RE = re.compile(r"\$[^$]*\$")
_MD_DISPLAY_MATH_RE = re.compile(r"\$\$[^$]*\$\$")
_LATEX_COMMAND_RE = re.compile(r"\\[A-Za-z]+")
_KOR_SPACED_RE = re.compile(r"(?<=[가-힣])\s+(?=[가-힣])")
_ALNUM_SPACED_RE = re.compile(r"(?<=[0-9A-Za-z])\s+(?=[0-9A-Za-z])")
_LINE_WS_RE = re.compile(r"[ \t]+")


def _first_result_payload(payload: dict) -> dict:
    results = payload.get("results") or {}
    if not results:
        return {}
    first_key = next(iter(results.keys()))
    return results.get(first_key) or {}


def _prepare_pdf_for_parsing(file_path: str) -> tuple[str, bool]:
    with fitz.open(file_path) as doc:
        page_count = doc.page_count
        if page_count <= MAX_PARSE_PAGES:
            return file_path, False

        page_indexes = sorted(random.sample(range(page_count), MAX_PARSE_PAGES))
        output_doc = fitz.open()
        for idx in page_indexes:
            output_doc.insert_pdf(doc, from_page=idx, to_page=idx)

    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".pdf")
    tmp_path = tmp.name
    tmp.close()
    output_doc.save(tmp_path)
    output_doc.close()
    return tmp_path, True


def _clean_markdown(text: str) -> str:
    if not text:
        return ""
    cleaned = text.replace("\r\n", "\n").replace("\r", "\n")
    cleaned = _TAG_RE.sub(" ", cleaned)
    cleaned = _MD_HEADING_RE.sub("", cleaned)
    cleaned = _MD_IMAGE_RE.sub(" ", cleaned)
    cleaned = _MD_DISPLAY_MATH_RE.sub(" ", cleaned)
    cleaned = _MD_INLINE_MATH_RE.sub(" ", cleaned)
    cleaned = _LATEX_COMMAND_RE.sub(" ", cleaned)
    cleaned = _KOR_SPACED_RE.sub("", cleaned)
    cleaned = _ALNUM_SPACED_RE.sub("", cleaned)

    lines = []
    for line in cleaned.split("\n"):
        line = _LINE_WS_RE.sub(" ", line).strip()
        if line:
            lines.append(line)
    return "\n".join(lines)


def _extract_text_from_content_list(content_list: list) -> str:
    if not content_list:
        return ""
    parts = []
    for item in content_list:
        if not isinstance(item, dict):
            continue
        item_type = item.get("type")
        if item_type == "text":
            value = item.get("text")
            if isinstance(value, str) and value.strip():
                parts.append(value)
        elif item_type == "table":
            table_body = item.get("table_body")
            if isinstance(table_body, str) and table_body.strip():
                parts.append(table_body)
    return "\n".join(parts).strip()


def parse_pdf_with_mineru(file_path: str) -> str:
    api_url = DEFAULT_API_URL
    backend = "pipeline"
    parse_method = "auto"
    lang_list = "korean"
    timeout_seconds = 300

    file_path_obj = Path(file_path)
    if not file_path_obj.exists():
        raise FileNotFoundError(f"PDF not found: {file_path}")

    prepared_path, should_cleanup = _prepare_pdf_for_parsing(str(file_path_obj))
    prepared_path_obj = Path(prepared_path)
    try:
        with prepared_path_obj.open("rb") as f:
            files = [
                ("files", (prepared_path_obj.name, f, "application/pdf")),
            ]
            data = {
                "backend": backend,
                "parse_method": parse_method,
                "lang_list": lang_list,
                "return_content_list": "true",
                "return_middle_json": "false",
                "return_images": "false",
                "return_md": "true",
                "response_format_zip": "false",
            }

            response = requests.post(api_url, files=files, data=data, timeout=timeout_seconds)
            response.raise_for_status()
            payload = response.json()
    finally:
        if should_cleanup and os.path.exists(prepared_path_obj):
            os.remove(prepared_path_obj)

    result = _first_result_payload(payload)
    content_list = result.get("content_list") or []
    text = _extract_text_from_content_list(content_list)
    if text:
        return _clean_markdown(text)
    md_content = (result.get("md_content") or "").strip()
    return _clean_markdown(md_content)
MAX_PARSE_PAGES = 20
