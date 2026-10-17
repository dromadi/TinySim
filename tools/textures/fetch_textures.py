"""Download the CC0 Poly Haven material maps used by TinySim into app/src/main/assets/textures.

Every map is stored as the original Poly Haven JPG (no re-encoding). index.json records, per material,
the source asset, URLs, MD5 checksums and the mean albedo colour the shader uses for tinting.
Requires Python 3 and Pillow. Run from the repository root:  python3 tools/textures/fetch_textures.py
"""
import hashlib, json, pathlib, urllib.request
from PIL import Image

# slot -> (Poly Haven asset, albedo/normal resolution, roughness resolution)
MATERIALS = {
    "sand": ("coast_sand_01", "2k", "1k"),
    "soil": ("dry_ground_rocks", "2k", "1k"),
    "grass": ("leafy_grass", "2k", "1k"),
    "asphalt": ("asphalt_02", "2k", "1k"),
    "concrete": ("brushed_concrete", "2k", "1k"),
    "paint": ("blue_metal_plate", "2k", "1k"),
    "wood": ("weathered_planks", "2k", "1k"),
    "plaster": ("clay_plaster", "2k", "1k"),
    "roof": ("clay_roof_tiles_02", "2k", "1k"),
}
MAPS = {"albedo": "Diffuse", "normal": "nor_gl", "rough": "Rough"}
root = pathlib.Path(__file__).resolve().parents[2]
out = root / "app/src/main/assets/textures"
out.mkdir(parents=True, exist_ok=True)

def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": "TinySim texture fetcher"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()

index = {"license": "CC0 1.0 (public domain), Poly Haven https://polyhaven.com/license", "materials": {}}
for slot, (asset, size, rough_size) in MATERIALS.items():
    files = json.loads(get(f"https://api.polyhaven.com/files/{asset}"))
    entry = {"asset": asset, "page": f"https://polyhaven.com/a/{asset}", "maps": {}}
    for kind, key in MAPS.items():
        resolution = rough_size if kind == "rough" else size
        info = files[key][resolution]["jpg"]
        data = get(info["url"])
        assert hashlib.md5(data).hexdigest() == info["md5"], f"checksum mismatch {asset} {kind}"
        name = f"{slot}_{kind}.jpg"
        (out / name).write_bytes(data)
        entry["maps"][kind] = {"file": name, "url": info["url"], "md5": info["md5"], "bytes": len(data)}
        print(f"{name}: {asset} {resolution} {len(data)} bytes")
    with Image.open(out / f"{slot}_albedo.jpg") as image:
        r, g, b = image.convert("RGB").resize((64, 64), Image.BOX).resize((1, 1), Image.BOX).getpixel((0, 0))
    entry["mean"] = [round(r / 255, 4), round(g / 255, 4), round(b / 255, 4)]
    index["materials"][slot] = entry
(out / "index.json").write_text(json.dumps(index, indent=1) + "\n")
print("total", sum(p.stat().st_size for p in out.iterdir()), "bytes")
