import logging

from app.mineru_client import parse_pdf_with_mineru

logger = logging.getLogger(__name__)


def extract_text_with_fallback(file_path: str, api_url: str = None) -> str:
    """
    PDF에서 텍스트 추출

    Args:
        file_path: PDF 파일 경로
        api_url: MinerU API URL (None이면 기본값 사용)
    """
    logger.info("PIPELINE START (MinerU) - API: %s", api_url or "default")
    text = parse_pdf_with_mineru(file_path, api_url)
    logger.info("MinerU text length: %s", len(text))
    logger.info("MinerU text preview (first 100 chars): %s", text[:100])
    return text
