import pytesseract
from pdf2image import convert_from_path

POPPLER_PATH = r"C:\poppler\Library\bin"
TESSERACT_PATH = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

pytesseract.pytesseract.tesseract_cmd = TESSERACT_PATH


def ocr_pdf(file_path: str) -> str:
    images = convert_from_path(file_path, poppler_path=POPPLER_PATH, dpi=400)
    texts = []

    for img in images:
        text = pytesseract.image_to_string(img, lang="kor", config="--oem 1 --psm 6")
        if text.strip():
            texts.append(text)

    return "\n".join(texts).strip()
