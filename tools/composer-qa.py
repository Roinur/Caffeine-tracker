"""Compatibility entry point for the current unified intake QA flow.
Run tools/github-media.ps1 first. The test refuses ordinary production mode.
"""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name("ux-qa.py")),run_name="__main__")
