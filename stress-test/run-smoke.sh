#!/bin/sh
set -eu

SIMULATION_CLASS="${GATLING_SIMULATION_CLASS:-com.exchange.stress.ledger.LedgerPostServiceSmokeSimulation}"

exec mvn -o -B -pl stress-test \
  "-Dgatling.simulationClass=${SIMULATION_CLASS}" \
  gatling:test
