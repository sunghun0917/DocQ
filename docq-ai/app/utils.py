import json
import re


_CONTROL_CHARS_RE = re.compile(r"[\x00-\x1f]")


def _strip_control_chars(value: str) -> str:
    # Remove raw control chars that break JSON (often from LLM output).
    return _CONTROL_CHARS_RE.sub(" ", value)


def _loads_first_json(candidate: str):
    decoder = json.JSONDecoder()
    cleaned = _strip_control_chars(candidate.strip())
    obj, _ = decoder.raw_decode(cleaned)
    return obj


def _extract_first_json_block(text: str) -> str | None:
    cleaned = _strip_control_chars(text)
    for start_idx, open_ch, close_ch in [
        (cleaned.find("["), "[", "]"),
        (cleaned.find("{"), "{", "}"),
    ]:
        if start_idx == -1:
            continue
        depth = 0
        in_string = False
        escape = False
        for i in range(start_idx, len(cleaned)):
            ch = cleaned[i]
            if escape:
                escape = False
                continue
            if ch == "\\":
                escape = True
                continue
            if ch == "\"":
                in_string = not in_string
                continue
            if in_string:
                continue
            if ch == open_ch:
                depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0:
                    return cleaned[start_idx : i + 1]
    return None


def extract_json(raw: str):
    """
    LLM 응답에서 JSON 배열 또는 객체만 안전하게 추출
    """
    raw = raw.strip()

    # JSON 배열
    if "[" in raw and "]" in raw:
        start = raw.find("[")
        end = raw.rfind("]") + 1
        candidate = raw[start:end]
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            block = _extract_first_json_block(candidate)
            if block:
                return _loads_first_json(block)
            return _loads_first_json(candidate)

    # JSON 객체
    if "{" in raw and "}" in raw:
        start = raw.find("{")
        end = raw.rfind("}") + 1
        candidate = raw[start:end]
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            block = _extract_first_json_block(candidate)
            if block:
                return _loads_first_json(block)
            return _loads_first_json(candidate)

    raise ValueError(f"JSON 추출 실패\n원본 응답:\n{raw}")
