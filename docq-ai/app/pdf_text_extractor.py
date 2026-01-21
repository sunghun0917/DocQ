import fitz
import random


def _sample_pages(total_pages: int, max_pages: int, seed: int) -> list:
    if max_pages <= 0 or total_pages <= max_pages:
        return list(range(total_pages))
    rnd = random.Random(seed)
    return sorted(rnd.sample(range(total_pages), max_pages))


def extract_text_from_pdf(file_path: str, max_pages: int = 20, seed: int = 42) -> str:
    texts = []

    with fitz.open(file_path) as doc:
        page_indexes = _sample_pages(doc.page_count, max_pages=max_pages, seed=seed)
        for idx in page_indexes:
            text = doc[idx].get_text()
            if text:
                texts.append(text)

    return "\n".join(texts).strip()
