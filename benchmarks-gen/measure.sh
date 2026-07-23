#!/usr/bin/env bash
# Benchmark de GENERACIÓN: tiempo de procesamiento de anotaciones, kmapx/KSP vs MapStruct/kapt,
# sobre el MISMO modelo (N pares generados por generate.py). Aísla la duración de la TAREA con
# --profile (excluye el arranque de Gradle), medianas de varias corridas. Indicativo, no
# gradle-profiler; los números varían por equipo (correlo para los tuyos).
set -euo pipefail
cd "$(dirname "$0")/.."
RUNS=${1:-5}

taskDur() { # $1 = html del profile, $2 = nombre de tarea
  python3 - "$1" "$2" <<'PYEOF'
import sys, re
t = open(sys.argv[1]).read()
m = re.search(r'>:benchmarks-gen:[a-z]+:'+sys.argv[2]+r'</td>\s*<td[^>]*>([\d.]+)s', t)
print(m.group(1) if m else "0")
PYEOF
}
median() { sort -n | awk '{a[NR]=$1} END{print (NR%2)?a[(NR+1)/2]:(a[NR/2]+a[NR/2+1])/2}'; }

ksp=(); stub=(); proc=()
for _ in $(seq "$RUNS"); do
  rm -rf build/reports/profile 2>/dev/null || true
  ./gradlew :benchmarks-gen:ksp:kspKotlin  --rerun-tasks --profile -q >/dev/null 2>&1
  ./gradlew :benchmarks-gen:kapt:kaptKotlin --rerun-tasks --profile -q >/dev/null 2>&1
  for f in build/reports/profile/profile-*.html; do
    k=$(taskDur "$f" kspKotlin);              [ "$k" != 0 ] && ksp+=("$k")
    s=$(taskDur "$f" kaptGenerateStubsKotlin); [ "$s" != 0 ] && stub+=("$s")
    p=$(taskDur "$f" kaptKotlin);             [ "$p" != 0 ] && proc+=("$p")
  done
done

mk=$(printf '%s\n' "${ksp[@]}"  | median)
ms=$(printf '%s\n' "${stub[@]}" | median)
mp=$(printf '%s\n' "${proc[@]}" | median)
echo
echo "=== Tiempo de GENERACIÓN (mediana de $RUNS corridas, tarea aislada) ==="
python3 -c "
mk,ms,mp = $mk,$ms,$mp
kapt = ms+mp
print(f'KSP  (kmapx):     {mk:.2f}s')
print(f'kapt (MapStruct): {kapt:.2f}s   (stubs {ms:.2f}s + proc {mp:.2f}s)')
print(f'-> kmapx genera {kapt/mk:.1f}x mas rapido')
"
