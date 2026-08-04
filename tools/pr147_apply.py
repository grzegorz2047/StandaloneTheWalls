import subprocess

saved = subprocess.check_output(
    ["git", "show", "200f62cf06d13617167d8b66c046a3339bba1800:tools/pr147_apply.py"],
    text=True,
)
old = '    private static final class PendingCommand {",\n    "failure mapping'
new = '    private static final class PendingCommand {""",\n    "failure mapping'
if saved.count(old) != 1:
    raise SystemExit("PR147 saved applicator delimiter shape changed")
script = saved.replace(old, new, 1)
exec(compile(script, "pr147-apply-fixed", "exec"), {"__name__": "__main__"})
