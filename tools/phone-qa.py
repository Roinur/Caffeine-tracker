"""ADB QA helper. Reads UI XML and sends input; screenshots require capture-github-media.ps1."""
import sys, subprocess, pathlib, xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding="utf-8")
import os
ADB = os.environ.get("CAFFEINE_ADB", "adb")
ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "qa" / "device"
OUT.mkdir(parents=True, exist_ok=True)
def adb(*args):
    p = subprocess.run([ADB, *map(str,args)], capture_output=True)
    if p.returncode: raise RuntimeError(p.stderr.decode("utf-8",errors="replace"))
    return p.stdout.decode("utf-8",errors="replace")
action = sys.argv[1]
if action == "dump":
    name = sys.argv[2]
    assert name.replace("-","").replace("_","").isalnum()
    print(adb("shell","uiautomator","dump","/sdcard/caffeine-tracker-qa.xml").strip())
    adb("pull","/sdcard/caffeine-tracker-qa.xml",OUT/(name+".xml"))
    root = ET.parse(OUT/(name+".xml")).getroot()
    for n in root.iter("node"):
        label=n.attrib.get("text") or n.attrib.get("content-desc")
        if label: print(label+" | "+n.attrib.get("bounds",""))
    print("UI TREE "+str(OUT/(name+".xml")))
elif action == "tap": print(adb("shell","input","tap",*sys.argv[2:]))
elif action == "swipe": print(adb("shell","input","swipe",*sys.argv[2:]))
elif action == "key": print(adb("shell","input","keyevent",sys.argv[2]))
elif action == "start": print(adb("shell","am","start","-n",sys.argv[2]))
elif action == "text": print(adb("shell","input","text",sys.argv[2]))
else: raise ValueError(action)
