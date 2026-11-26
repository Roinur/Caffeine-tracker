"""Repeatable gfxinfo scroll runs; never takes screenshots or changes app data."""
import sys, subprocess, pathlib, time, re, xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding="utf-8")
import os
ADB = os.environ.get("CAFFEINE_ADB", "adb")
PKG="com.roinur.caffeinetracker"
ROOT=pathlib.Path(__file__).resolve().parents[1]
label=sys.argv[1]
out=ROOT/"qa"/"device"/("scroll-"+label); out.mkdir(parents=True,exist_ok=True)
def adb(*args):
 return subprocess.run([ADB,*map(str,args)],capture_output=True,check=True).stdout.decode("utf-8",errors="replace")
def node(label):
 adb("shell","uiautomator","dump","/sdcard/caffeine-scroll-ui.xml")
 root=ET.fromstring(adb("shell","cat","/sdcard/caffeine-scroll-ui.xml"))
 for n in root.iter("node"):
  if (n.get("text") or n.get("content-desc"))==label:
   x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds")))
   return ((x1+x2)//2,(y1+y2)//2)
 raise RuntimeError("Missing control "+label)
def tap(label): adb("shell","input","tap",*node(label))
adb("shell","am","start","-S","-n",PKG+"/.app.MainActivity")
time.sleep(1)
for surface in ["home","history"]:
 if surface=="history": tap("Intag")
 for run in range(1,4):
  # Warm once; reset at a stable idle screen before the exact same gesture loop.
  time.sleep(.5)
  adb("shell","dumpsys","gfxinfo",PKG,"reset")
  for direction in [1,1,-1,-1,1,1,-1,-1]:
   a,b=(2300,1000) if direction==1 else (1000,2300)
   adb("shell","input","swipe",630,a,630,b,450)
  text=adb("shell","dumpsys","gfxinfo",PKG,"framestats")
  (out/(surface+"-"+str(run)+".txt")).write_text(text,encoding="utf-8")
  summary=[x.strip() for x in text.splitlines() if any(k in x for k in ["Total frames rendered:","Janky frames:","50th percentile:","90th percentile:","95th percentile:","99th percentile:","Number Missed Vsync:","Number Slow UI thread:"])]
  print(surface,run," | ".join(summary),flush=True)
adb("shell","input","keyevent",4)
