"""Functional smoke test on synthetic GitHub data only. Does not take screenshots."""
import subprocess,pathlib,xml.etree.ElementTree as E,re,json,sqlite3,sys,time
sys.stdout.reconfigure(encoding="utf-8")
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/"qa"/"device";OUT.mkdir(parents=True,exist_ok=True)
import os
ADB = os.environ.get("CAFFEINE_ADB", "adb")
PKG="com.roinur.caffeinetracker"
def adb(*args): return subprocess.run([ADB,*map(str,args)],check=True,capture_output=True).stdout
activities=adb("shell","dumpsys","activity","activities").decode(errors="replace")
assert re.search(r"topResumedActivity=.*caffeinetracker/.*GitHubMediaLauncher",activities), "Requires GitHub media launcher in foreground"
def tree(name):
 adb("shell","uiautomator","dump","/sdcard/caffeine-composer-test.xml")
 data=adb("shell","cat","/sdcard/caffeine-composer-test.xml")
 (OUT/(name+".xml")).write_bytes(data)
 return E.fromstring(data)
def point(n):
 x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds")))
 return ((x1+x2)//2,(y1+y2)//2)
def find(root,label):
 return next(n for n in root.iter("node") if n.get("text")==label or n.get("content-desc")==label)
def tap(label):
 root=tree("composer-current");n=find(root,label);parents={c:p for p in root.iter() for c in p}
 while n.get("clickable")!="true" and n in parents: n=parents[n]
 adb("shell","input","tap",*point(n))
def fill(label,text):
 root=tree("composer-current");n=find(root,label);parents={c:p for p in root.iter() for c in p}
 while n.get("class")!="android.widget.EditText" and n in parents:n=parents[n]
 assert n.get("class")=="android.widget.EditText", "Missing editor for "+label
 adb("shell","input","tap",*point(n))
 adb("shell","input","keycombination",113,29)
 adb("shell","input","text",text.replace(" ","%s"))
def texts(root):return [n.get("text") for n in root.iter("node")]
def rows():
 p=OUT/"composer-qa-final.db";p.write_bytes(adb("exec-out","run-as",PKG,"cat","databases/caffeine_tracker_github_media.db"))
 with sqlite3.connect(p) as db:return [json.loads(r[0]) for r in db.execute("select payload from intakes")]


def snapshot():
 p=OUT/"ux-test-final.db"
 p.write_bytes(adb("exec-out","run-as",PKG,"cat","databases/caffeine_tracker_github_media.db"))
 with sqlite3.connect(p) as db:
  return {table:[json.loads(row[0]) for row in db.execute("select payload from "+table)] for table in ["intakes","drinks"]}

def wait_for(test):
 for _ in range(40):
  data=snapshot()
  if test(data):return data
  time.sleep(.15)
 raise AssertionError("Database condition did not become true")

def visible_tap(label):
 for _ in range(5):
  root=tree("ux-current")
  try:n=find(root,label)
  except StopIteration:
   adb("shell","input","swipe",630,2300,630,1050,350);continue
  parents={c:p for p in root.iter() for c in p}
  while n.get("clickable")!="true" and n in parents:n=parents[n]
  adb("shell","input","tap",*point(n));return
 raise AssertionError("Not visible: "+label)

def new_rows(data):return [r for r in data["intakes"] if not r["id"].startswith("github-")]

assert len(rows())==60,"Use a fresh media session"
start=tree("ux-final-home")
assert "Favoriter" in texts(start) and "Lägg till" not in texts(start)
assert not any("upptag" in (t or "").lower() for t in texts(start))
tap("Energidryck")
labels=texts(tree("ux-final-selected"))
assert "Lägg till" in labels and "Fler detaljer" in labels
assert "Tid till 90 % upptag (minuter)" not in labels
assert "Dricktid (minuter)" not in labels
p=point(find(tree("ux-final-ready"),"Lägg till"))
adb("shell","input","tap",*p);adb("shell","input","tap",*p)
data=wait_for(lambda d:len(new_rows(d))==1)
assert new_rows(data)[0]["caffeineMg"]==80.0
visible_tap("Ångra")
wait_for(lambda d:not new_rows(d))
print("PASS: favorite + Add, no advanced fields, immediate double-tap guard, Undo new intake",flush=True)

fill("Dryck","QA Brew");tap("Skapa ”QA Brew”")
fill("Portion","200 ml");adb("shell","input","keyevent",4);fill("Totalt koffein (mg)","123")
adb("shell","input","keyevent",4);visible_tap("Lägg till")
data=wait_for(lambda d:len(new_rows(d))==1)
custom=new_rows(data)[0]
assert custom["name"]=="QA Brew" and custom["caffeineMg"]==123.0 and custom["servingUnit"]=="200 ml"
visible_tap("Spara som favorit")
data=wait_for(lambda d:any(r["name"]=="QA Brew" for r in d["drinks"]))
assert next(r for r in data["drinks"] if r["name"]=="QA Brew")["volumeMl"]==200.0
print("PASS: inline custom intake and optional favorite with structured volume",flush=True)

visible_tap("Historik")
original=next(r for r in snapshot()["intakes"] if r["id"]=="github-am-0")
visible_tap("Filterkaffe")
labels=texts(tree("ux-final-edit"))
assert "Spara ändringar" in labels and "Tid till 90 % upptag (minuter)" not in labels
visible_tap("Fler detaljer");fill("Anteckning","UI QA")
adb("shell","input","keyevent",4);visible_tap("Spara ändringar")
data=wait_for(lambda d:next(r for r in d["intakes"] if r["id"]==original["id"])["note"]=="UI QA")
changed=next(r for r in data["intakes"] if r["id"]==original["id"])
assert changed==dict(original,note="UI QA")
visible_tap("Ångra")
data=wait_for(lambda d:next(r for r in d["intakes"] if r["id"]==original["id"])==original)
print("PASS: compact history edit preserves all hidden values; Undo restores exact row",flush=True)

visible_tap("Filterkaffe");visible_tap("Ta bort intag");visible_tap("Ta bort")
wait_for(lambda d:not any(r["id"]==original["id"] for r in d["intakes"]))
visible_tap("Ångra")
wait_for(lambda d:any(r==original for r in d["intakes"]))
print("PASS: delete confirmation and Undo deletion restore original metadata",flush=True)

# Return Home after the history checks. Further variant/layout checks are recorded in the QA report.
visible_tap("Tillbaka")
print("All primary UX regression checks passed on separate GitHub data.",flush=True)
