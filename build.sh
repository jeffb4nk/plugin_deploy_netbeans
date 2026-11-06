#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [ -z "${NB_HOME:-}" ]; then
    if [ -d /usr/lib/apache-netbeans ]; then
        NB_HOME=/usr/lib/apache-netbeans
    else
        NB_HOME=/snap/netbeans/149/netbeans
    fi
fi
ANT="${NB_HOME}/extide/ant/bin/ant"

if [ ! -x "$ANT" ]; then
    echo "ERROR: no se encontró Ant en: $ANT"
    echo "Define NB_HOME con la ruta de instalación de NetBeans."
    exit 1
fi

if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_BIN="$(command -v java || true)"
    if [ -n "$JAVA_BIN" ]; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
        export JAVA_HOME
    fi
fi

cat > nbproject/platform.properties <<EOF
nbplatform.active=default
nbplatform.default.netbeans.dest.dir=${NB_HOME}
nbplatform.default.harness.dir=${NB_HOME}/harness
EOF

"$ANT" -f build.xml -Duse.pack200=false nbm-nosignature

echo
echo "Artefacto generado:"
ls -1 build/*.nbm 2>/dev/null || echo "(no se encontró ningún .nbm)"
