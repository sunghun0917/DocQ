from app.chunking import chunk_text_by_count
from app.upstage_client import acall_llm as call_llm
from app.prompt import FAST_MULTI_QUIZ_PROMPT, QUIZ_REFINE_PROMPT
from app.utils import extract_json

import asyncio
import json
import re

TARGET = 22
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
    except Exception:
        return []

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
    chunks = chunk_text_by_count(text, max_chunks=2)

    quizzes = []
    per_chunk = TARGET // len(chunks)

    tasks = [generate_chunk_quiz(chunk, per_chunk) for chunk in chunks]
    results = await asyncio.gather(*tasks)

    for res in results:
        quizzes.extend(res)

    quizzes = _dedupe_quizzes(quizzes)

    attempts = 0
    fallback_context = " ".join(chunks)
    while len(quizzes) < TARGET and attempts < 2:
        missing = TARGET - len(quizzes)
        refill = await generate_chunk_quiz(fallback_context, missing)
        quizzes.extend(refill)
        quizzes = _dedupe_quizzes(quizzes)
        attempts += 1

    final_attempts = 0
    while len(quizzes) < TARGET and final_attempts < MAX_FINAL_ATTEMPTS:
        if len(quizzes) < REFINE_MIN_COUNT:
            quizzes = await _refine_quizzes(text, quizzes, TARGET)
        if len(quizzes) >= TARGET:
            break
        missing = TARGET - len(quizzes)
        refill = await generate_chunk_quiz(fallback_context, missing)
        quizzes.extend(refill)
        quizzes = _dedupe_quizzes(quizzes)
        final_attempts += 1

    if len(quizzes) < MIN_RETURN:
        extra_attempts = 0
        while len(quizzes) < MIN_RETURN and extra_attempts < 3:
            missing = MIN_RETURN - len(quizzes)
            refill = await generate_chunk_quiz(fallback_context, missing)
            quizzes.extend(refill)
            quizzes = _dedupe_quizzes(quizzes)
            extra_attempts += 1
    return quizzes[:TARGET]
