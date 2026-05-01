from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path


APP_DIR_NAME = "MobileNetworkAnalyzerRuntime"
BUNDLE_DIR_NAME = "MobileNetworkAnalyzerApp"


def resource_root() -> Path:
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        return Path(sys._MEIPASS)
    return Path(__file__).resolve().parents[1]


def bundle_source() -> Path:
    return resource_root() / BUNDLE_DIR_NAME


def target_root() -> Path:
    local_app_data = os.environ.get("LOCALAPPDATA")
    if not local_app_data:
        raise RuntimeError("LOCALAPPDATA is not available on this Windows machine.")
    return Path(local_app_data) / APP_DIR_NAME


def install_bundle(source: Path, target: Path) -> None:
    if target.exists():
        shutil.rmtree(target)
    shutil.copytree(source, target)


def launch_server(target: Path) -> None:
    launch_cmd = target / "Launch-Mobile-Network-Analyzer.cmd"
    if not launch_cmd.exists():
        raise FileNotFoundError(f"Launch script was not found: {launch_cmd}")
    subprocess.Popen(
        [str(launch_cmd)],
        cwd=str(target),
        creationflags=subprocess.CREATE_NEW_CONSOLE
    )


def main() -> int:
    source = bundle_source()
    if not source.exists():
        raise FileNotFoundError(f"Embedded application package was not found: {source}")

    target = target_root()
    install_bundle(source, target)
    launch_server(target)
    print(f"Application files installed to: {target}")
    print("The Mobile Network Analyzer launcher window has been opened.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # pragma: no cover - packaging helper
        print(f"Launcher failed: {error}")
        input("Press Enter to close this window...")
        raise SystemExit(1)
