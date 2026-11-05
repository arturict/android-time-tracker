"""Fail if tracked files include device data, local tools, or private settings."""
import re
import subprocess
import sys

files = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
forbidden = re.compile(r"(^|/)(\.local-tools|\.gradle|build|databases|device-data|captures|exports)(/|$)|\.(db|sqlite|sqlite3)(-|$)|\.(apk|aab|hprof|jks|keystore)$|(^|/)(local\.properties|keystore\.properties|\.env)(\.|$)", re.I)
bad = [path for path in files if path and forbidden.search(path)]
if bad:
    print("Refusing publication of private data or local output:\n" + "\n".join(bad), file=sys.stderr)
    sys.exit(1)
print(f"Public-content check passed for {sum(bool(path) for path in files)} tracked files.")
