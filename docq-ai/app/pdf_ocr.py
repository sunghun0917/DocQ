import pytesseract
import os
import platform
from dotenv import load_dotenv

load_dotenv() # Load .env file

from pdf2image import convert_from_path

import os
import platform

# Load environment variables checks
# In Docker, these will be likely None, which is fine as they are in PATH
POPPLER_PATH = os.getenv("POPPLER_PATH")
TESSERACT_PATH = os.getenv("TESSERACT_PATH")

# Windows fallback for local development if not in .env and not in PATH
if platform.system() == "Windows":
    if not POPPLER_PATH:
        # Default fallback location if user hasn't set .env (optional safety net)
        # Using the values that were previously hardcoded as defaults
        POPPLER_PATH = r"C:\poppler\Library\bin"
    if not TESSERACT_PATH:
        TESSERACT_PATH = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

# If TESSERACT_PATH is found (either from env or fallback), set it.
# If it's None (Linux/Docker), tesseract_cmd defaults to 'tesseract' which works for PATH.
if TESSERACT_PATH:
    pytesseract.pytesseract.tesseract_cmd = TESSERACT_PATH


def ocr_pdf(file_path: str) -> str:
    images = convert_from_path(file_path, poppler_path=POPPLER_PATH, dpi=400)
    texts = []

    for img in images:
        text = pytesseract.image_to_string(img, lang="kor", config="--oem 1 --psm 6")
        if text.strip():
            texts.append(text)

    return "\n".join(texts).strip()
