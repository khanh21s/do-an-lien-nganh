#!/usr/bin/env bash
# Java service verify (docs/architecture.md §1.1): compile + unit tests with Maven/JDK 21
# in Docker, reusing the host ~/.m2 cache.
#   scripts/verify-java.sh content-service          # one service ("content" also works)
#   scripts/verify-java.sh auth practice             # several
#   scripts/verify-java.sh                           # all 5
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ALL=(auth-service content-service practice-service vocabulary-service subscription-service)

if [ "$#" -eq 0 ]; then set -- "${ALL[@]}"; fi
mkdir -p "$HOME/.m2"

for svc in "$@"; do
  svc="${svc%-service}-service"
  if [ ! -f "$ROOT/services/$svc/pom.xml" ]; then
    echo "verify-java: unknown service '$svc' (expected one of: ${ALL[*]})" >&2
    exit 2
  fi
  echo "== $svc: mvn test"
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 -v "$HOME/.m2":/tmp/.m2 \
    -v "$ROOT/services/$svc":/app -w /app maven:3.9-eclipse-temurin-21 \
    mvn -B -q -Dmaven.repo.local=/tmp/.m2/repository test
done
echo "verify-java: OK ($*)"
