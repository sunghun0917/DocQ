from typing import List


def chunk_text(
    text: str,
    min_len: int = 300,
    max_len: int = 1200
) -> List[str]:
    """
    긴 텍스트를 문단 기준으로 청킹
    """
    paragraphs = [p.strip() for p in text.split("\n") if p.strip()]
    chunks = []
    current = ""

    for p in paragraphs:
        # 현재 chunk에 더해도 max 안 넘으면 추가
        if len(current) + len(p) <= max_len:
            current += " " + p if current else p
        else:
            # 현재 chunk 확정
            if len(current) >= min_len:
                chunks.append(current)

            # 새 chunk 시작
            current = p

    # 마지막 chunk 처리
    if len(current) >= min_len:
        chunks.append(current)

    return chunks


def chunk_text_by_count(text: str, max_chunks: int = 3) -> List[str]:
    """
    전체 텍스트를 대략 max_chunks 개수로 나눔
    """
    total_len = len(text)
    if total_len < 500:
        return [text]

    # 단순하게 N등분 목표 길이 산정
    target_len = (total_len // max_chunks) + 200  # 약간의 여유

    return chunk_text(text, min_len=100, max_len=target_len)
