#!/bin/sh
set -eu

SIMULATION_CLASS="${GATLING_SIMULATION_CLASS:-}"

if [ -n "${SIMULATION_CLASS}" ]; then
  exec mvn -o -B -pl stress-test \
    "-Dgatling.simulationClass=${SIMULATION_CLASS}" \
    gatling:test
fi

# With no selector, the plugin runs every smoke simulation. It also proceeds to
# later simulations after an assertion failure so each API surface is checked.
exec mvn -o -B -pl stress-test gatling:test
