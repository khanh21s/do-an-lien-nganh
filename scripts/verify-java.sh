#!/usr/bin/env bash
# Java service verify (docs/architecture.md §1.1): compile + unit tests with Maven/JDK 21
# in Docker, reusing the host ~/.m2 cache. Runs from the services/ aggregator pom, so the
# shared module services/common (parroto-common) is always built + tested with the service.
#   scripts/verify-java.sh content-service          # one service ("content" also works)
#   scripts/verify-java.sh auth practice             # several
#   scripts/verify-java.sh common                    # only the shared module
#   scripts/verify-java.sh                           # common + all 5
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ALL=(auth-service content-service practice-service vocabulary-service subscription-service)

mkdir -p "$HOME/.m2"

mvn_in_docker() {
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 -v "$HOME/.m2":/tmp/.m2 \
    -v "$ROOT/services":/app -w /app maven:3.9-eclipse-temurin-21 \
    mvn -B -q -Dmaven.repo.local=/tmp/.m2/repository "$@"
}

if [ "$#" -eq 0 ]; then
  echo "== common + ${ALL[*]}: mvn test"
  mvn_in_docker test
  echo "verify-java: OK (common ${ALL[*]})"
  exit 0
fi

modules=()
for svc in "$@"; do
  if [ "$svc" = "common" ]; then
    modules+=(common)
    continue
  fi
  svc="${svc%-service}-service"
  if [ ! -f "$ROOT/services/$svc/pom.xml" ]; then
    echo "verify-java: unknown service '$svc' (expected common or one of: ${ALL[*]})" >&2
    exit 2
  fi
  modules+=("$svc")
done
pl="$(IFS=,; echo "${modules[*]}")"
echo "== $pl (+ common): mvn test"
mvn_in_docker -pl "$pl" -am test
echo "verify-java: OK ($*)"
