#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
LOG="perimetre-loop.log"
PASS=0
while true; do
  PASS=$((PASS + 1))
  echo "=== passage $PASS — $(date '+%d/%m %H:%M') ===" | tee -a "$LOG"
  if (cd src/test/e2e && node scripts/traceability.mjs > /tmp/guard.log 2>&1); then
    echo "Garde-fou vert : passage final." | tee -a "$LOG"
    claude -p --dangerously-skip-permissions \
      "Exécute la section « Fin de travail » de PERIMETRE.md. Le garde-fou de traçabilité passe désormais. Lance mvn verify complet (suite de bout en bout et test de visibilité du jeu de démonstration compris), corrige tout ce qui casse, puis rédige le compte rendu final chiffré dans docs/PERIMETRE.md et commite." \
      2>&1 | tee -a "$LOG"
    echo "Terminé." | tee -a "$LOG"
    break
  fi
  claude -p --dangerously-skip-permissions \
    "Exécute PERIMETRE.md. Tu démarres dans une session neuve, sans mémoire des passages précédents : lis docs/PERIMETRE.md, puis lance node src/test/e2e/scripts/traceability.mjs depuis src/test/e2e pour obtenir la liste exacte des manques. Prends le PREMIER parcours manquant dans l'ordre des tableaux de la section 4 de TESTS-E2E.md et réalise-le en entier : fonctionnalité manquante s'il y a lieu, tests à tous les niveaux, cycle court puis vérification finale (mvn verify -DskipE2E=true et test de bout en bout ciblé), commit, état des lieux à jour dans docs/PERIMETRE.md. Puis termine ton tour : un autre passage prendra le parcours suivant. Tu ne poses aucune question et tu ne demandes aucune confirmation." \
    2>&1 | tee -a "$LOG"
  sleep 5
done
