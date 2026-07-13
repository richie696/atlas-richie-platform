#!/usr/bin/env python3
"""
PaddleOCR wrapper script for atlas-richie-component-ocr-paddle.

用法:
  python3 paddle_ocr.py --image <path> --lang <ch|en|...> --model-dir <path>

输出 (stdout, 单行 JSON):
  {"items": [{"text": "...", "bbox": [[x1,y1],...], "score": 0.95}, ...]}

错误 (exit 2 + stderr):
  {"error": "paddleocr not installed"}
"""
import argparse
import json
import sys


def main():
    parser = argparse.ArgumentParser(description="PaddleOCR wrapper")
    parser.add_argument("--image", required=True, help="Path to image file")
    parser.add_argument(
        "--lang",
        default="ch",
        help="PaddleOCR lang code: ch | en | french | german | korean | japan | ... (default: ch)",
    )
    parser.add_argument(
        "--model-dir", default=None, help="PaddleOCR model directory (optional)"
    )
    args = parser.parse_args()

    try:
        from paddleocr import PaddleOCR
    except ImportError as e:
        sys.stderr.write(json.dumps({"error": "paddleocr not installed: " + str(e)}))
        sys.exit(2)

    try:
        kwargs = {"use_angle_cls": True, "lang": args.lang, "show_log": False}
        if args.model_dir:
            kwargs["det_model_dir"] = args.model_dir
            kwargs["rec_model_dir"] = args.model_dir
            kwargs["cls_model_dir"] = args.model_dir
        ocr = PaddleOCR(**kwargs)
        raw = ocr.ocr(args.image, cls=True)
    except Exception as e:
        sys.stderr.write(json.dumps({"error": "paddleocr failed: " + str(e)}))
        sys.exit(3)

    items = []
    # raw 形态: List[Page], Page: List[Line], Line: [box, (text, score)]
    if raw:
        for page in raw:
            if not page:
                continue
            for line in page:
                if not line or len(line) < 2:
                    continue
                box = line[0]
                text_score = line[1]
                if not text_score or len(text_score) < 2:
                    continue
                text = text_score[0]
                score = float(text_score[1])
                items.append({"text": text, "bbox": box, "score": score})

    sys.stdout.write(json.dumps({"items": items}, ensure_ascii=False))


if __name__ == "__main__":
    main()