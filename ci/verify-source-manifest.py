import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
data=json.loads((root/'SOURCE-SHA256.json').read_text())['source_sha256_by_path']
assert data
bad=[]
for name,expected in data.items():
    p=root/name
    if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=expected:bad.append(name)
assert not bad, 'Source manifest mismatch: '+repr(bad)
print('Verified',len(data),'source hashes')
