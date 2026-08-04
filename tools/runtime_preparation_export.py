from pathlib import Path

WORKFLOW = Path(".github/workflows/runtime-preparation-export.yml")
MARKER = "      - name: Apply deterministic runtime patch\n        shell: python\n        run: |\n"
FORMATTER_STEP = "      - name: Apply repository formatter\n"

workflow = WORKFLOW.read_text(encoding="utf-8")
start = workflow.index(MARKER) + len(MARKER)
end = workflow.index(FORMATTER_STEP, start)
script = "".join(
    line[10:] if line.startswith("          ") else line
    for line in workflow[start:end].splitlines(keepends=True)
)

original = """def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)
"""
robust = """def replace_once(text, old, new, label):
    if label == "ordered preparation publication":
        method_start = text.index("    private void stabilizeMatchSnapshots(")
        method_end = text.index("    private void removeFailedMembers(", method_start)
        return text[:method_start] + new + "\\n" + text[method_end:]
    if label == "default preparation map holder":
        method_start = text.index("    private static Throwable unwrap(")
        enum_start = text.index("    private enum State {", method_start)
        enum_end = enum_start + len("    private enum State {\\n")
        return text[:method_start] + new + text[enum_end:]
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)
"""

if script.count(original) != 1:
    raise SystemExit("replace_once helper shape changed")
script = script.replace(original, robust, 1)
exec(compile(script, "runtime-preparation-export", "exec"), {"__name__": "__main__"})
