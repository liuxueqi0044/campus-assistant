#!/usr/bin/env python3
"""M00.02 契约自检：YAML/JSON 可解析、$ref 可解析、错误码表结构合法。

用法（仓库根目录）：python contracts/tools/check_contracts.py

只依赖 Python 3 与 PyYAML（`python -m pip install pyyaml`），不需要网络与 Node 工具链，
可被 M00.06 的 CI 直接调用。检查失败时以非零退出码结束。
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import yaml

CONTRACTS = Path(__file__).resolve().parent.parent
ERROR_CSV = CONTRACTS / "openapi" / "campus-error-codes.csv"
RETRY_ACTIONS = {"RETRY", "USER_FIX", "OPERATOR"}
REQUIRED_ENVELOPE_FIELDS = {"code", "message", "traceId"}
ALLOWED_PATH_PREFIXES = ("/app-api/campus/v1", "/admin-api/campus/v1", "/health/")
HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
EVENT_SCHEMA = CONTRACTS / "events" / "envelope.schema.json"

problems: list[str] = []


def fail(message: str) -> None:
    problems.append(message)


def load_yaml(path: Path) -> object:
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001 - 自检脚本需要给出可读原因
        fail(f"{path.relative_to(CONTRACTS)}: YAML 解析失败: {exc}")
        return None


def walk_refs(node: object, path: Path):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "$ref" and isinstance(value, str):
                yield value
            else:
                yield from walk_refs(value, path)
    elif isinstance(node, list):
        for item in node:
            yield from walk_refs(item, path)


def resolve_pointer(document: object, pointer: str) -> bool:
    current = document
    for raw_token in pointer.strip("/").split("/") if pointer.strip("/") else []:
        token = raw_token.replace("~1", "/").replace("~0", "~")
        if isinstance(current, dict) and token in current:
            current = current[token]
        else:
            return False
    return True


def resolved_ref(path: Path, ref: str) -> object | None:
    """解析本地引用并返回目标，供结构门禁复用。"""
    if "://" in ref:
        return None
    target_part, _, pointer = ref.partition("#")
    target = path if not target_part else (path.parent / target_part).resolve()
    if not target.is_file():
        return None
    try:
        document = load_yaml(target) if target.suffix in {".yaml", ".yml"} else json.loads(
            target.read_text(encoding="utf-8"))
    except Exception:  # 具体解析错误由原有检查报告
        return None
    current = document
    for raw_token in pointer.strip("/").split("/") if pointer.strip("/") else []:
        token = raw_token.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or token not in current:
            return None
        current = current[token]
    return current


def check_refs(path: Path, document: object) -> None:
    for ref in walk_refs(document, path):
        if ref.startswith("#"):
            if not resolve_pointer(document, ref[1:]):
                fail(f"{path.relative_to(CONTRACTS)}: 内部 $ref 无法解析: {ref}")
            continue
        if "://" in ref:
            # 外部 URL（含 $schema）不在离线自检范围内
            continue
        target_part, _, pointer = ref.partition("#")
        target = (path.parent / target_part).resolve()
        if not target.is_file():
            fail(f"{path.relative_to(CONTRACTS)}: $ref 指向的文件不存在: {ref}")
            continue
        target_document = load_yaml(target) if target.suffix in {".yaml", ".yml"} else json.loads(
            target.read_text(encoding="utf-8"))
        if pointer and not resolve_pointer(target_document, pointer):
            fail(f"{path.relative_to(CONTRACTS)}: 跨文件 $ref 无法解析: {ref}")


def check_openapi() -> None:
    for path in sorted((CONTRACTS / "openapi").rglob("*.yaml")):
        document = load_yaml(path)
        if document is None:
            continue
        check_refs(path, document)
        if path.parent.name == "components":
            continue
        version = str(document.get("info", {}).get("version", ""))
        if not version:
            fail(f"{path.relative_to(CONTRACTS)}: 缺少 info.version")
        paths = document.get("paths")
        if not isinstance(paths, dict) or not paths:
            fail(f"{path.relative_to(CONTRACTS)}: 缺少 paths")
            continue
        operation_ids: set[str] = set()
        for path_key, operations in paths.items():
            if not path_key.startswith(ALLOWED_PATH_PREFIXES):
                fail(f"{path.relative_to(CONTRACTS)}: 路径 {path_key} 未落在约定入口前缀内")
            if not isinstance(operations, dict):
                fail(f"{path.relative_to(CONTRACTS)}: 路径 {path_key} 的 operations 结构非法")
                continue
            for method, operation in operations.items():
                if method not in HTTP_METHODS:
                    continue
                if not isinstance(operation, dict) or not operation.get("responses"):
                    fail(f"{path.relative_to(CONTRACTS)}: {method.upper()} {path_key} 缺少 responses")
                    continue
                operation_id = operation.get("operationId")
                if not isinstance(operation_id, str) or not operation_id:
                    fail(f"{path.relative_to(CONTRACTS)}: {method.upper()} {path_key} 缺少 operationId")
                elif operation_id in operation_ids:
                    fail(f"{path.relative_to(CONTRACTS)}: operationId 重复: {operation_id}")
                else:
                    operation_ids.add(operation_id)
                check_response_examples(path, method, path_key, operation.get("responses", {}))


def check_response_examples(path: Path, method: str, path_key: str, responses: object) -> None:
    if not isinstance(responses, dict):
        return
    for response in responses.values():
        response_source = path
        if isinstance(response, dict) and "$ref" in response:
            response_ref = response["$ref"]
            response_source = path if response_ref.startswith("#") else (path.parent / response_ref.partition("#")[0]).resolve()
            resolved_response = resolved_ref(path, response_ref)
        else:
            resolved_response = response
        if not isinstance(resolved_response, dict):
            continue
        content = resolved_response.get("content", {})
        for media in content.values() if isinstance(content, dict) else []:
            examples = media.get("examples", {}) if isinstance(media, dict) else {}
            for name, example in examples.items() if isinstance(examples, dict) else []:
                resolved_example = resolved_ref(response_source, example["$ref"]) \
                    if isinstance(example, dict) and "$ref" in example else example
                if not isinstance(resolved_example, dict) or not ({"value", "externalValue"} & resolved_example.keys()):
                    fail(f"{path.relative_to(CONTRACTS)}: {method.upper()} {path_key} "
                         f"的示例 {name} 不是合法 Example Object（需要 value 或 externalValue）")


def check_json_files() -> None:
    for path in sorted(CONTRACTS.rglob("*.json")):
        try:
            document = json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001
            fail(f"{path.relative_to(CONTRACTS)}: JSON 解析失败: {exc}")
            continue
        if path.name.endswith("schema.json"):
            check_refs(path, document)
            continue
        body = None
        if isinstance(document, dict):
            body = document.get("body")
            if path.parent.name == "examples":
                body = document.get("value")
        if isinstance(body, dict):
            missing = REQUIRED_ENVELOPE_FIELDS - body.keys()
            if missing:
                fail(f"{path.relative_to(CONTRACTS)}: 信封缺少字段 {sorted(missing)}")


def check_event_contract() -> None:
    try:
        schema = json.loads(EVENT_SCHEMA.read_text(encoding="utf-8"))
        pattern_text = schema["$defs"]["forbiddenKey"]["pattern"]
        forbidden_pattern = re.compile(pattern_text)
    except Exception as exc:  # noqa: BLE001
        fail(f"events/envelope.schema.json: 敏感键规则不可读取: {exc}")
        return

    # 这些写法归一化后都必须与 Java FORBIDDEN_PAYLOAD_KEYS 命中同一集合。
    for key in ("id_card_no", "ID-CARD-NO", "id-card-number", "i_d_c_a_r_d", "pickup_code", "CERT-NO"):
        if forbidden_pattern.fullmatch(key) is None:
            fail(f"events/envelope.schema.json: 敏感键归一化漏判 {key}")
    for key in ("orderId", "identityStatus", "pickupLocation"):
        if forbidden_pattern.fullmatch(key) is not None:
            fail(f"events/envelope.schema.json: 敏感键规则误判普通字段 {key}")

    required = set(schema.get("required", []))
    timestamp_pattern = re.compile(schema["properties"]["occurredAt"]["pattern"])
    for path in sorted((CONTRACTS / "stubs" / "events").glob("*.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        for index, delivery in enumerate(document.get("deliveries", []), start=1):
            envelope = delivery.get("envelope", {})
            missing = required - envelope.keys()
            if missing:
                fail(f"{path.relative_to(CONTRACTS)}: deliveries[{index}] 缺少 {sorted(missing)}")
            occurred_at = envelope.get("occurredAt")
            if not isinstance(occurred_at, str) or timestamp_pattern.search(occurred_at) is None:
                fail(f"{path.relative_to(CONTRACTS)}: deliveries[{index}].occurredAt 必须带时区偏移")


def check_error_codes() -> None:
    if not ERROR_CSV.is_file():
        fail("openapi/campus-error-codes.csv: 文件缺失")
        return
    names: set[str] = set()
    codes: set[int] = set()
    for number, line in enumerate(ERROR_CSV.read_text(encoding="utf-8").splitlines(), start=1):
        line = line.strip()
        if not line or line.startswith("name,"):
            continue
        columns = line.split(",", 4)
        if len(columns) != 5:
            fail(f"campus-error-codes.csv:{number}: 需要 5 列")
            continue
        name, code, http_status, retry_action, message = (column.strip() for column in columns)
        if name in names:
            fail(f"campus-error-codes.csv:{number}: 名称重复 {name}")
        names.add(name)
        if not code.isdigit():
            fail(f"campus-error-codes.csv:{number}: 编码必须是数字")
        elif int(code) in codes:
            fail(f"campus-error-codes.csv:{number}: 编码重复 {code}")
        else:
            codes.add(int(code))
        if retry_action not in RETRY_ACTIONS:
            fail(f"campus-error-codes.csv:{number}: requiredAction 非法 {retry_action}")
        if not message.startswith('"') or not message.endswith('"'):
            fail(f"campus-error-codes.csv:{number}: message 必须用引号包裹")
    if not names:
        fail("campus-error-codes.csv: 没有登记任何错误码")


def main() -> int:
    check_openapi()
    check_json_files()
    check_error_codes()
    check_event_contract()
    if problems:
        print("契约自检失败：")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("契约自检通过：OpenAPI 最小语义、YAML/JSON、$ref、错误码与事件规则一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
