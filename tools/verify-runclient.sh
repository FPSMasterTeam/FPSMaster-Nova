#!/usr/bin/env bash
# Usage: tools/verify-runclient.sh <version> [--dry-run|--check]
set -euo pipefail
export NOVA_VERIFY_REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 - "$@" <<'PY'
import os
import pathlib
import re
import signal
import struct
import subprocess
import sys
import tempfile
import time
import uuid
import zlib

VERSIONS = ("26.2", "1.21.11", "1.21.1", "1.20.1", "1.21.8", "1.19.2")
STAGES = ("main-menu", "create-world", "world", "clickgui", "cosmetics", "settings", "returned-menu")
FILES = ("01-menu", "02-create-world", "03-world", "04-clickgui", "05-cosmetics", "06-settings", "07-returned-menu")
# Sample every 16 pixels. Reject black, pure-color, and near-pure-color images.
SAMPLE_STEP = 16
MIN_VARIANCE = 25.0
MIN_COLORS = 16
MIN_VISIBLE = 0.05
MIN_HEART_PIXELS = 4
MIN_CENTER_CONTRAST = 50
REPO = pathlib.Path(os.environ["NOVA_VERIFY_REPO"])
WORK = REPO.parent / ".work"
SHOTS = WORK / "verification" / "nova-shots"
BAD = re.compile(
    r"InvalidMixinException|ClassCastException|NoSuchMethodError|Scanned 0 target\(s\)"
    r"|Mixin apply (?:for mod .*? )?failed|MixinApplyError|InjectionError|InvalidInjectionException"
    r"|nova-verify failure=", re.IGNORECASE
)

def frame_stats(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("invalid PNG signature")
    at, compressed = 8, bytearray()
    width = height = channels = 0
    while at < len(data):
        length = struct.unpack_from(">I", data, at)[0]
        kind = data[at + 4:at + 8]
        chunk = data[at + 8:at + 8 + length]
        crc = struct.unpack_from(">I", data, at + 8 + length)[0]
        if zlib.crc32(kind + chunk) & 0xffffffff != crc:
            raise ValueError("invalid PNG checksum")
        if kind == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", chunk)
            if depth != 8 or color not in (2, 6) or compression or filtering or interlace:
                raise ValueError("expected non-interlaced 8-bit RGB/RGBA framebuffer PNG")
            channels = 3 if color == 2 else 4
        elif kind == b"IDAT":
            compressed.extend(chunk)
        elif kind == b"IEND":
            break
        at += length + 12
    if width < 320 or height < 200 or width * height > 40_000_000:
        raise ValueError(f"invalid framebuffer dimensions: {width}x{height}")
    raw = zlib.decompress(compressed)
    stride = width * channels
    if len(raw) != (stride + 1) * height:
        raise ValueError("invalid PNG pixel length")
    previous, colors, values, visible = bytearray(stride), set(), [], 0
    heart_pixels, center = 0, []
    for y in range(height):
        start = y * (stride + 1)
        mode, row = raw[start], bytearray(raw[start + 1:start + stride + 1])
        for x in range(stride):
            left = row[x - channels] if x >= channels else 0
            above = previous[x]
            corner = previous[x - channels] if x >= channels else 0
            if mode == 1:
                row[x] = (row[x] + left) & 255
            elif mode == 2:
                row[x] = (row[x] + above) & 255
            elif mode == 3:
                row[x] = (row[x] + (left + above) // 2) & 255
            elif mode == 4:
                p = left + above - corner
                distances = (abs(p - left), abs(p - above), abs(p - corner))
                predictor = (left, above, corner)[distances.index(min(distances))]
                row[x] = (row[x] + predictor) & 255
            elif mode != 0:
                raise ValueError(f"invalid PNG filter: {mode}")
        if y % SAMPLE_STEP == 0:
            for x in range(0, width, SAMPLE_STEP):
                rgb = tuple(row[x * channels:x * channels + 3])
                colors.add(rgb)
                values.append(sum(rgb) / 3)
                visible += sum(rgb) > 60
        if y >= height * 2 // 3:
            for x in range(width):
                red, green, blue = row[x * channels:x * channels + 3]
                heart_pixels += red >= 120 and red > green * 1.5 and red > blue * 1.5
        if abs(y - height // 2) <= 12:
            for x in range(max(0, width // 2 - 12), min(width, width // 2 + 13)):
                center.append(sum(row[x * channels:x * channels + 3]) / 3)
        previous = row
    mean = sum(values) / len(values)
    variance = sum((value - mean) ** 2 for value in values) / len(values)
    return len(values), len(colors), variance, visible / len(values), heart_pixels, max(center) - min(center)

def validate(text, version, run, shots=SHOTS):
    errors = []
    hits = BAD.findall(text)
    if hits:
        errors.append(f"forbidden log patterns: {len(hits)}")
    ready = f"nova-verify ready run={run} version={version} smoke=true gameDir=/tmp/nova-verify/{version}"
    if ready not in text:
        errors.append("missing in-game smoke/gameDir acknowledgement")
    cursor = 0
    for stage, filename in zip(STAGES, FILES):
        marker = f"nova-verify stage={stage} run={run} "
        at = text.find(marker, cursor)
        if at < 0:
            errors.append(f"missing or out-of-order stage: {stage}")
            continue
        cursor = at + len(marker)
        image = shots / f"{version}-{filename}.png"
        if not image.is_file():
            errors.append(f"missing screenshot: {stage}")
            continue
        try:
            samples, colors, variance, visible, hearts, contrast = frame_stats(image)
            print(f"FRAME {image.name}: samples={samples} colors={colors} variance={variance:.2f} visible={visible:.3f} bottomRed={hearts} centerContrast={contrast:.2f}")
            if colors < MIN_COLORS or variance < MIN_VARIANCE or visible < MIN_VISIBLE:
                errors.append(f"black-or-flat-frame: {stage} (colors={colors}, variance={variance:.2f}, visible={visible:.3f})")
            if stage == "world" and (hearts < MIN_HEART_PIXELS or contrast < MIN_CENTER_CONTRAST):
                errors.append(f"missing world HUD structure: bottomRed={hearts}, centerContrast={contrast:.2f}")
        except (ValueError, struct.error, zlib.error) as error:
            errors.append(f"invalid PNG: {stage}: {error}")
    if not re.search(r'\[runtime-probe\].*"reason"\s*:\s*"shutdown"', text):
        errors.append("missing orderly client shutdown")
    return errors

def write_result(result, run, status, code, started, log):
    result.write_text(f"run={run}\nexit={code}\nelapsedSeconds={time.monotonic() - started:.0f}\n"
                      f"log={log} (last lines: tail -20 {log})\n{status}\n")

def supervise(command, cwd, env, destination, timeout, lock=None, owner="", result=None, run=""):
    """Run the client with a hard timeout; always kill its process group and release the lock."""
    started = time.monotonic()
    locked = False
    try:
        if lock:
            try:
                lock.mkdir()
            except FileExistsError:
                raise SystemExit("Desktop is locked. Wait for the orchestrator's handoff; do not remove it.")
            locked = True
            (lock / "owner").write_text(owner)
        with destination.open("w") as output:
            process = subprocess.Popen(command, cwd=cwd, env=env, stdout=output,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            try:
                return process.wait(timeout=timeout), started
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                if result:
                    write_result(result, run, f"FAIL: timeout/interrupted after {timeout}s limit; client process group killed, lock released",
                                 process.returncode, started, destination)
                raise SystemExit("FAIL: timeout/interrupted, this run is not accepted")
    finally:
        if locked:
            (lock / "owner").unlink()
            lock.rmdir()

def main():
    if len(sys.argv) not in (2, 3) or sys.argv[1] not in VERSIONS:
        raise SystemExit("Usage: tools/verify-runclient.sh <26.2|1.21.11|1.21.1|1.20.1|1.21.8|1.19.2> [--dry-run|--check]")
    version = sys.argv[1]
    mode = sys.argv[2] if len(sys.argv) == 3 else ""
    if mode not in ("", "--dry-run", "--check", "--self-test"):
        raise SystemExit("Unknown mode")
    if mode == "--self-test":
        def png(path, kind="varied"):
            def chunk(kind, body):
                return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xffffffff)
            raw = bytearray()
            for y in range(208):
                raw.append(0)
                for x in range(320):
                    rgb = (x % 256, y % 256, (x + y) % 256)
                    if kind == "near-flat":
                        rgb = (100 + (x // 16) % 6, 100 + (y // 16) % 6, 100 + ((x + y) // 16) % 6)
                    elif kind == "black":
                        rgb = (0, 0, 0)
                    elif kind == "loading":
                        rgb = (x % 128, x % 128, y % 256)
                    if kind == "varied":
                        if y >= 180 and 100 <= x < 110:
                            rgb = (220, 0, 0)
                        if (x == 160 and abs(y - 104) <= 6) or (y == 104 and abs(x - 160) <= 6):
                            rgb = (255, 255, 255)
                    raw.extend(rgb)
            path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 320, 208, 8, 2, 0, 0, 0))
                             + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))
        with tempfile.TemporaryDirectory(prefix="nova-verify-test-") as temporary:
            shots = pathlib.Path(temporary)
            text = f"nova-verify ready run=test version={version} smoke=true gameDir=/tmp/nova-verify/{version}\n"
            for stage, filename in zip(STAGES, FILES):
                png(shots / f"{version}-{filename}.png")
                text += f"nova-verify stage={stage} run=test width=854 height=480\n"
            text += '[runtime-probe] {"reason":"shutdown"}'
            assert not validate(text, version, "test", shots)
            for pattern in ("InvalidMixinException", "ClassCastException", "NoSuchMethodError",
                            "Scanned 0 target(s)", "Mixin apply for mod test failed", "nova-verify failure=black-or-flat-frame"):
                assert validate(text + pattern, version, "test", shots), pattern
            assert validate(text.replace("stage=world ", "stage=missing "), version, "test", shots)
            assert validate(text.replace("smoke=true", "smoke=false"), version, "test", shots)
            image = shots / f"{version}-{FILES[4]}.png"
            for kind in ("black", "near-flat"):
                png(image, kind)
                assert validate(text, version, "test", shots), kind
            png(image)
            world_image = shots / f"{version}-{FILES[2]}.png"
            png(world_image, "loading")
            assert any("missing world HUD" in error for error in validate(text, version, "test", shots))
            png(world_image)
            for filename in FILES:
                image = shots / f"{version}-{filename}.png"
                saved = image.read_bytes()
                image.unlink()
                assert validate(text, version, "test", shots), filename
                image.write_bytes(saved)
            reference = os.environ.get("NOVA_VERIFY_REFERENCE")
            if reference:
                samples, colors, variance, visible, hearts, contrast = frame_stats(pathlib.Path(reference))
                print(f"REFERENCE {reference}: samples={samples} colors={colors} variance={variance:.2f} visible={visible:.3f} bottomRed={hearts} centerContrast={contrast:.2f}")
                assert colors >= MIN_COLORS and variance >= MIN_VARIANCE and visible >= MIN_VISIBLE
                assert hearts >= MIN_HEART_PIXELS and contrast >= MIN_CENTER_CONTRAST
        with tempfile.TemporaryDirectory(prefix="nova-verify-timeout-") as temporary:
            base = pathlib.Path(temporary)
            for name, command in (("term", ["sleep", "1000"]), ("kill", ["sh", "-c", "trap '' TERM; sleep 1000"])):
                lock, result, out = base / f"{name}.lock", base / f"{name}-result.txt", base / f"{name}.log"
                started = time.monotonic()
                try:
                    supervise(command, base, os.environ.copy(), out, 2, lock, "test\n", result, "test")
                    raise AssertionError("timeout not enforced")
                except SystemExit as error:
                    assert "timeout" in str(error)
                body = result.read_text()
                assert not lock.exists() and "FAIL: timeout" in body and "exit=-" in body and f"log={out}" in body, body
                assert time.monotonic() - started < (6 if name == "term" else 10)
                print(f"TIMEOUT {name}: " + body.replace("\n", " | "))
        print("PASS: evidence checker accepts valid evidence and rejects fatal logs, black frames, missing stages/flags/images; timeout kills and releases lock")
        return
    SHOTS.mkdir(parents=True, exist_ok=True)
    log = SHOTS / f"{version}-console.log"
    run_file = SHOTS / f"{version}-run.txt"
    if mode == "--check":
        errors = validate(log.read_text(errors="replace"), version, run_file.read_text().strip())
        for error in errors:
            print("FAIL:", error)
        raise SystemExit(bool(errors))

    game = pathlib.Path("/tmp/nova-verify") / version
    marker = game / ".nova-verifier"
    if game.exists() and (not marker.is_file() or marker.read_text().strip() != str(REPO)):
        raise SystemExit(f"Refusing existing unowned gameDir: {game}")
    game.mkdir(parents=True, exist_ok=True)
    marker.write_text(str(REPO) + "\n")
    options = game / "options.txt"
    if not options.exists():
        options.write_text("guiScale:2\nfullscreen:false\nrenderDistance:6\nsimulationDistance:5\nmaxFps:60\npauseOnLostFocus:false\ngamma:1.0\n")
    soak = int(os.environ.get("NOVA_VERIFY_SOAK_SECONDS", "180"))
    if not 1 <= soak <= 600:
        raise SystemExit("NOVA_VERIFY_SOAK_SECONDS must be 1..600 (acceptance defaults to 180)")
    run = uuid.uuid4().hex
    command = [
        str(REPO / "gradlew"), "--offline", "--max-workers=1", f":{version}:runClient",
        "-Dfpsmaster.smoke=true", f"-Dfpsmaster.verify.gameDir={game}",
        f"-Dfpsmaster.verify.shots={SHOTS}", f"-Dfpsmaster.verify.version={version}",
        f"-Dfpsmaster.verify.run={run}", f"-Dfpsmaster.verify.soakSeconds={soak}",
        "-Dfpsmaster.verify.auto=true",
        f"-Dfpsmaster.verify.dryRun={'true' if mode == '--dry-run' else 'false'}",
    ]
    if mode == "--dry-run":
        command += ["-x", f":{version}:downloadAssets"]
    env = os.environ.copy()
    env.setdefault("JAVA_HOME", "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home")
    # A dry run executes Gradle's launch preflight but stops JavaExec before any game/window starts.
    lock = WORK / "desktop.lock" if mode != "--dry-run" else None
    if lock:
        run_file.write_text(run + "\n")
    destination = SHOTS / f"{version}-launch-check.log" if mode == "--dry-run" else log
    result = SHOTS / f"{version}-result.txt" if lock else None
    print(f"{version}: gameDir={game}; log={destination}; soak={soak}s", flush=True)
    if lock:
        print("MANUAL MODE: follow docs/VERIFY_RUNCLIENT.md. Hold each screen >=3s; review every framebuffer image.", flush=True)
    code, started = supervise(command, REPO, env, destination, 240 if mode == "--dry-run" else 1140, lock,
                              f"Nova verify-runclient {version}; pid={os.getpid()}; start={time.ctime()}\n", result, run)
    text = destination.read_text(errors="replace")
    if mode == "--dry-run":
        expected = f"nova-verify-launch smoke=true gameDir={game} version={version}"
        if code != 0 or expected not in text:
            raise SystemExit(f"FAIL: launch preflight; inspect {destination}")
        print("PASS: JavaExec smoke forwarding and isolated workingDir; no game launched")
        return
    errors = validate(text, version, run)
    if code != 0:
        errors.append(f"Gradle/client exit code {code}")
    write_result(result, run, "\n".join(errors) if errors else "AUTOMATED GATES PASS; manual image review still required",
                 code, started, destination)
    for error in errors:
        print("FAIL:", error)
    if errors:
        raise SystemExit(1)
    print("PASS: ordered stages, framebuffer evidence, error gates and orderly shutdown. Review images before signing off.")
def interrupted(signum, frame):
    raise KeyboardInterrupt()

signal.signal(signal.SIGTERM, interrupted)
main()
PY
