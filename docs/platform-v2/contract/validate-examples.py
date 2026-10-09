#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""契约样例校验：用 JSON Schema (Draft 2020-12) 校验 contract/examples/ 下的每个样例。

为什么要有它：设计文档 §14.3 要求"请求/响应字段严格按 JSON Schema 校验"，§20.1 把
"契约层"列为"阻断发布候选"的一层。阶段 0 只有文档与 Schema，所以先用这个脚本把
"Schema 与样例自洽"这件事验掉；阶段 1 应把它搬进 CI。

用法（在 docs/platform-v2 目录下）：
    C:\Python314\python.exe contract/validate-examples.py
退出码 0 = 全部通过；1 = 有样例不符合 Schema。
"""
from __future__ import annotations

import json
import pathlib
import sys

try:
    from jsonschema import Draft202012Validator
except ImportError:  # pragma: no cover
    print("需要 jsonschema：pip install jsonschema")
    sys.exit(2)

HERE = pathlib.Path(__file__).resolve().parent


def load_json(path: pathlib.Path):
    """读一个 JSON 文件；失败时**点名文件**而不是抛裸 traceback。

    为什么不是小事：这个脚本要在 CI 里当门禁跑，而它原先遇到坏 JSON 会打出
    `json.decoder.JSONDecodeError: ... line 1 column 1` —— **不带文件名**。
    门禁输出里看不出是哪个文件坏了，等于让人去猜（本轮就真的踩到：一个被意外写成
    UTF-8 BOM 的样例只报 "Unexpected UTF-8 BOM"，得自己去二分）。
    这里统一返回 (数据, 错误信息)，由调用方汇总进 problems。
    """
    try:
        return json.loads(path.read_text(encoding="utf-8")), None
    except OSError as exc:
        return None, f"{path.name}: 读不到（{exc}）"
    except json.JSONDecodeError as exc:
        return None, (f"{path.name}: 不是合法 JSON（{exc.msg}，行 {exc.lineno} 列 {exc.colno}）"
                      f"；若提示 BOM，说明文件被写成了带 BOM 的 UTF-8")


# 样例文件 -> 校验它的 Schema
CASES = {
    "examples/request-detail-page.json": "execution-request.schema.json",
    "examples/result-succeeded.json": "execution-result.schema.json",
    "examples/error-policy-denied.json": "execution-result.schema.json",
    "examples/event-status-changed.json": "execution-event.schema.json",
    "examples/callback-succeeded.json": "provider-callback.schema.json",
}


def main() -> int:
    problems = []
    checked = 0
    for rel, schema_name in CASES.items():
        schema, err = load_json(HERE / schema_name)
        if err:
            problems.append(err)
            continue
        Draft202012Validator.check_schema(schema)  # Schema 自身必须合法
        instance, err = load_json(HERE / rel)
        if err:
            problems.append(err)
            continue
        errors = sorted(Draft202012Validator(schema).iter_errors(instance),
                        key=lambda e: list(e.path))
        checked += 1
        if errors:
            for e in errors:
                path = "/".join(str(p) for p in e.path) or "(root)"
                problems.append(f"{rel} vs {schema_name}: {path}: {e.message}")
        else:
            print(f"  OK   {rel}  <-  {schema_name}")

    # 错误码表与 result schema 的 errorCode 枚举必须一致（防止两处漂移）
    codes, err = load_json(HERE / "error-codes.json")
    if err:
        problems.append(err)
        codes = None
    result, err = load_json(HERE / "execution-result.schema.json")
    if err:
        problems.append(err)
        result = None
    if codes is not None and result is not None:
        declared = {c["code"] for c in codes["codes"]}
        in_schema = set(result["$defs"]["error"]["properties"]["errorCode"]["enum"])
        if declared != in_schema:
            problems.append("error-codes.json 与 execution-result.schema.json 的 errorCode 不一致："
                            f"仅表中有 {sorted(declared - in_schema)}；仅 schema 有 {sorted(in_schema - declared)}")
        else:
            print(f"  OK   error-codes.json 与 execution-result.schema.json 一致（{len(declared)} 个码）")

    print(f"\nchecked={checked} problems={len(problems)}")
    for p in problems:
        print("  FAIL " + p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
