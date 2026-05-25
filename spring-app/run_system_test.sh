source ../.env

set -euo pipefail

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="./logs/system-test-$TIMESTAMP.log"
DATASET_DIR="./src/test/resources/test_datasets/test_documents/system-test"
START_DELAY_SECONDS="${START_DELAY_SECONDS:-15}"
STEP_DELAY_SECONDS="${STEP_DELAY_SECONDS:-10}"

mkdir -p "$DATASET_DIR"
rm -f "$DATASET_DIR"/Test{A,B,C,D}.md

write_file() {
    local file_path="$1"
    shift

    printf '%b\n' "$1" >"$file_path"
}

prepare_condition_1() {
    write_file "$DATASET_DIR/TestA.md" "# TestA\n\nCondition 1: initial content for TestA."
    write_file "$DATASET_DIR/TestB.md" "# TestB\n\nCondition 1: initial content for TestB."
    write_file "$DATASET_DIR/TestC.md" "# TestC\n\nCondition 1: initial content for TestC."
}

prepare_condition_2() {
    write_file "$DATASET_DIR/TestA.md" "# TestA\n\nCondition 2: updated content for TestA."
    write_file "$DATASET_DIR/TestD.md" "# TestD\n\nCondition 2: newly added content for TestD."
}

prepare_condition_3() {
    rm -f \
        "$DATASET_DIR/TestA.md" \
        "$DATASET_DIR/TestB.md"
}

cleanup() {
    if [[ -n "${MUTATION_PID:-}" ]] && kill -0 "$MUTATION_PID" 2>/dev/null; then
        kill "$MUTATION_PID" 2>/dev/null || true
    fi
}

trap cleanup EXIT INT TERM

(
    sleep "$START_DELAY_SECONDS"
    prepare_condition_1

    sleep "$STEP_DELAY_SECONDS"
    prepare_condition_2

    sleep "$STEP_DELAY_SECONDS"
    prepare_condition_3
) &
MUTATION_PID=$!

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="--app.mode=api --config.path=./config.test.yml" \
    2>&1 | tee "$LOG_FILE"