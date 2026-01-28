from app.chunking import chunk_text_by_count
from app.upstage_client import acall_llm as call_llm
from app.prompt import FAST_MULTI_QUIZ_PROMPT, QUIZ_REFINE_PROMPT
from app.utils import extract_json

import asyncio
import json
import re
import random

TARGET = 22
MAX_SINGLE_CALL_CHARS = 15000  # 약 5k 토큰 (Ensure stability)
REFINE_MIN_COUNT = 20
MAX_FINAL_ATTEMPTS = 2
MIN_RETURN = 20
_QUESTION_NORMALIZE_RE = re.compile(r"\s+")
_ANSWER_NORMALIZE_RE = re.compile(r"\s+")
_SANITIZE_RE = re.compile(r"[^0-9A-Za-z가-힣\s\.\,\?\!\:\;\-\(\)\"\'\+\=\*\/%]")


def _normalize_question(value: str) -> str:
    return _QUESTION_NORMALIZE_RE.sub(" ", value.strip().lower())


def _normalize_answer(value: str) -> str:
    return _ANSWER_NORMALIZE_RE.sub(" ", value.strip().lower())


def _sanitize_text(value: str) -> str:
    cleaned = _SANITIZE_RE.sub(" ", value)
    cleaned = _QUESTION_NORMALIZE_RE.sub(" ", cleaned)
    return cleaned.strip()


def _resolve_answer_index(options: list, answer_text: str) -> int | None:
    try:
        return options.index(answer_text)
    except ValueError:
        pass

    normalized = _normalize_answer(answer_text)
    for idx, opt in enumerate(options):
        if _normalize_answer(opt) == normalized:
            return idx
    return None


def _is_valid_quiz(q: dict) -> bool:
    if not isinstance(q, dict):
        return False
    if "question" not in q or "options" not in q or "answerText" not in q:
        return False
    if not isinstance(q["question"], str) or len(q["question"].strip()) < 5:
        return False
    if not isinstance(q["options"], list) or len(q["options"]) != 4:
        return False
    if not all(isinstance(opt, str) and opt.strip() for opt in q["options"]):
        return False
    if not isinstance(q["answerText"], str) or not q["answerText"].strip():
        return False
    return True


def _coerce_quiz_list(parsed):
    if isinstance(parsed, list):
        return parsed
    if isinstance(parsed, dict):
        for key in ("quizzes", "items", "data", "results"):
            value = parsed.get(key)
            if isinstance(value, list):
                return value
    return []


def _normalize_quiz_fields(q: dict) -> dict:
    if not isinstance(q, dict):
        return {}
    if "answerText" not in q:
        for key in ("answer", "correct", "correctAnswer"):
            if key in q:
                q["answerText"] = q.get(key)
                break
    if "options" not in q:
        for key in ("choices", "answers"):
            if key in q:
                q["options"] = q.get(key)
                break
    return q


def _pad_options(options: list, answer_text: str) -> list:
    if not isinstance(options, list):
        options = []
    cleaned = [_sanitize_text(str(opt)) for opt in options if str(opt).strip()]
    if answer_text and answer_text not in cleaned:
        cleaned.append(answer_text)

    # Ensure at least 4 options by adding simple distractors
    filler_pool = [
        "해당 없음",
        "모두 해당",
        "기타",
        "잘못된 설명",
        "정답 없음",
    ]
    while len(cleaned) < 4:
        candidate = random.choice(filler_pool)
        if candidate not in cleaned:
            cleaned.append(candidate)

    return cleaned[:4]


def _dedupe_quizzes(quizzes: list) -> list:
    seen = set()
    unique = []
    for q in quizzes:
        key = _normalize_question(q["question"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(q)
    return unique


def _limit_text(text: str, max_chars: int = 8000) -> str:
    if len(text) <= max_chars:
        return text
    return text[:max_chars]


async def generate_chunk_quiz(chunk: str, per_chunk: int) -> list:
    prompt = FAST_MULTI_QUIZ_PROMPT.format(count=per_chunk)
    raw = await call_llm(prompt + "\n\n" + chunk)
    try:
        parsed = extract_json(raw)
    except Exception as e:
        print(f"JSON Parsing Failed: {e}")
        print(f"Raw Response specific: {raw[:500]}...")  # 로그에서 확인 위해 앞부분 출력
        return []

    cleaned = []
    for q in _coerce_quiz_list(parsed):
        q = _normalize_quiz_fields(q)
        if not q:
            continue
        if not isinstance(q, dict):
            continue
        answer_text = _sanitize_text(str(q.get("answerText", "")))
        if "answerIndex" in q and isinstance(q["answerIndex"], int):
            if "options" in q and 0 <= q["answerIndex"] < len(q["options"]):
                answer_text = _sanitize_text(str(q["options"][q["answerIndex"]]))
        q["options"] = _pad_options(q.get("options", []), answer_text)
        if "question" in q:
            q["question"] = _sanitize_text(str(q["question"]))
        if "explanation" in q:
            q["explanation"] = _sanitize_text(str(q["explanation"]))
        if answer_text:
            q["answerText"] = answer_text
        if not _is_valid_quiz(q):
            continue
        answer_index = _resolve_answer_index(q["options"], str(q["answerText"]))
        if answer_index is None:
            continue
        q["answerIndex"] = answer_index
        cleaned.append(q)

    print(
        f"[quiz] requested={per_chunk} parsed={len(_coerce_quiz_list(parsed))} "
        f"valid={len(cleaned)} text_len={len(chunk)}"
    )
    return cleaned


async def _refine_quizzes(text: str, quizzes: list, target: int) -> list:
    if not quizzes:
        return quizzes

    draft_json = json.dumps(quizzes, ensure_ascii=False)
    prompt = QUIZ_REFINE_PROMPT.format(
        count=target,
        study_text=_limit_text(text),
        draft_json=draft_json,
    )
    raw = await call_llm(prompt)

    try:
        parsed = extract_json(raw)
    except Exception:
        return quizzes

    cleaned = []
    for q in parsed:
        if not isinstance(q, dict):
            continue
        if "options" in q and isinstance(q["options"], list):
            q["options"] = [_sanitize_text(str(opt)) for opt in q["options"]]
        if "question" in q:
            q["question"] = _sanitize_text(str(q["question"]))
        if "explanation" in q:
            q["explanation"] = _sanitize_text(str(q["explanation"]))
        if "answerText" in q:
            q["answerText"] = _sanitize_text(str(q["answerText"]))
        if not _is_valid_quiz(q):
            continue
        answer_index = _resolve_answer_index(q["options"], str(q["answerText"]))
        if answer_index is None:
            continue
        q["answerIndex"] = answer_index
        cleaned.append(q)

    cleaned = _dedupe_quizzes(cleaned)
    if len(cleaned) >= target:
        return cleaned[:target]
    return quizzes


async def generate_20_quizzes_fast(text: str) -> list:
    max_chunks = min(6, max(3, len(text) // 6000 + 1))
    chunks = chunk_text_by_count(text, max_chunks=max_chunks)
    per_chunk = max(1, (TARGET + len(chunks) - 1) // len(chunks)) + 1

    tasks = [generate_chunk_quiz(chunk, per_chunk) for chunk in chunks]
    results = await asyncio.gather(*tasks)

    quizzes = []
    for res in results:
        quizzes.extend(res)

    quizzes = _dedupe_quizzes(quizzes)
    return quizzes[:TARGET]
