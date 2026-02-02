from typing import List


def chunk_text(
    text: str,
    min_len: int = 300,
    max_len: int = 1200,
) -> List[str]:
    """
    Split text by paragraph boundaries into chunks.
    """
    paragraphs = [p.strip() for p in text.split("\n") if p.strip()]
    chunks = []
    current = ""

    for p in paragraphs:
        if len(current) + len(p) <= max_len:
            current += " " + p if current else p
        else:
            if len(current) >= min_len:
                chunks.append(current)
            current = p

    if len(current) >= min_len:
        chunks.append(current)

    return chunks


def chunk_text_by_count(text: str, max_chunks: int = 3) -> List[str]:
    """
    Split text into at most max_chunks chunks.
    Falls back to length-based splitting if there are too few paragraphs.
    """
    total_len = len(text)
    if total_len < 500:
        return [text]

    target_len = (total_len // max_chunks) + 200
    chunks = chunk_text(text, min_len=100, max_len=target_len)
    if len(chunks) >= 2:
        return chunks

    hard_target = max(1000, total_len // max_chunks)
    return [text[i : i + hard_target] for i in range(0, total_len, hard_target)]
