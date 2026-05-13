source ../.env

usage() {
    echo "Usage: ./review.sh <diff-file-path>"
    echo "   or: ./review.sh --use-mock"
}

DIFF_FILE=""
USE_MOCK=false

for arg in "$@"; do
    case "$arg" in
        --use-mock)
            USE_MOCK=true
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            if [[ -n "$DIFF_FILE" ]]; then
                echo "Error: only one diff file path is allowed."
                usage
                exit 1
            fi
            DIFF_FILE="$arg"
            ;;
    esac
done

if [[ "$USE_MOCK" != true && -z "$DIFF_FILE" ]]; then
    echo "Error: no diff file path provided."
    usage
    exit 1
fi

SPRING_ARGS=("--app.mode=review")
if [[ -n "$DIFF_FILE" ]]; then
    SPRING_ARGS+=("$DIFF_FILE")
fi
if [[ "$USE_MOCK" == true ]]; then
    SPRING_ARGS+=("--use-mock")
fi
SPRING_ARGUMENTS="${SPRING_ARGS[*]}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo "Starting Spring Boot application with arguments: $SPRING_ARGUMENTS"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    2>&1 | tee ../logs/spring-app-$TIMESTAMP.log
