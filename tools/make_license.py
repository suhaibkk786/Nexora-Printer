"""Nexora Printer: makes license codes that unlock Pro without a Google Play subscription.

  python tools/make_license.py init                      # once: makes the private key (stays on this PC) and prints the public key
  python tools/make_license.py code "Suhaib - own use"   # a code that never expires
  python tools/make_license.py code "Demo shop" --until 2027-03-31

The PRIVATE key is kept in  ~/.nexora/license_private.pem  - never put it in the repository. The app only holds the public key,
so a code cannot be forged without the private key. Needs:  pip install cryptography
"""
import argparse, base64, datetime, pathlib, sys
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

KEY = pathlib.Path.home() / ".nexora" / "license_private.pem"
b64 = lambda b: base64.urlsafe_b64encode(b).decode().rstrip("=")

def public_b64(priv):
    return base64.b64encode(priv.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()

def load():
    if not KEY.exists(): sys.exit(f"No private key at {KEY}. Run:  python tools/make_license.py init")
    return serialization.load_pem_private_key(KEY.read_bytes(), None)

def main():
    ap = argparse.ArgumentParser(); sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("init")
    c = sub.add_parser("code"); c.add_argument("label"); c.add_argument("--until", help="last day, yyyy-mm-dd (default: never expires)")
    sub.add_parser("public")
    a = ap.parse_args()
    if a.cmd == "init":
        if KEY.exists(): sys.exit(f"Already exists: {KEY} (delete it only if you want every old code to stop working)")
        KEY.parent.mkdir(parents=True, exist_ok=True)
        priv = ec.generate_private_key(ec.SECP256R1())
        KEY.write_bytes(priv.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        print("Private key written to", KEY, "- keep it safe and secret.\nPublic key (put it in LicenseVerifier.PUBLIC_KEY):\n" + public_b64(priv))
    elif a.cmd == "public":
        print(public_b64(load()))
    else:
        label = a.label.replace("|", "/").strip()
        until = a.until.replace("-", "") if a.until else "0"
        if until != "0": datetime.datetime.strptime(until, "%Y%m%d")
        payload = f"{label}|{until}".encode()
        sig = load().sign(payload, ec.ECDSA(hashes.SHA256()))
        print("NXP1." + b64(payload) + "." + b64(sig))

main()
