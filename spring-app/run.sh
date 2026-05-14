source ../.env

usage() {
    echo "Usage: ./run.sh [mode] [mode-specific-arguments] -a [additional-properties-file]"
    echo ""
    echo "Modes:"
    echo "  question_answer: ask questions about the datasets."
    echo "    ./run.sh question_answer <query>"
    echo ""
    echo "  sync: synchronize database with local datasets."
    echo ""
    echo "  review: review code changes based on a diff file."
    echo "    ./run.sh review <diff-file-path>"
    echo "    ./run.sh review --use-mock"
    echo ""
    echo "Global options:"
    echo "  -a, --additional-properties-file <file-path>"
    echo "    Load extra Spring properties file for overrides."
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

if [[ $# -gt 0 ]]; then
    MODE="$1"
    shift
fi

if [[ -z "$MODE" ]]; then
    echo "Error: no mode specified."
    usage
    exit 1
fi

APP_ARGS=()
MODE_ARGS=()
ADDITIONAL_PROPERTIES_FILE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -a|--additional-properties-file)
            if [[ -n "$ADDITIONAL_PROPERTIES_FILE" ]]; then
                echo "Error: only one additional properties file can be specified."
                usage
                exit 1
            fi
            if [[ -z "${2:-}" ]]; then
                echo "Error: -a/--additional-properties-file requires a file path."
                usage
                exit 1
            fi
            ADDITIONAL_PROPERTIES_FILE="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            MODE_ARGS+=("$1")
            shift
            ;;
    esac
done

if [[ "$MODE" == "review" ]]; then
    DIFF_FILE=""
    USE_MOCK=false

    for arg in "${MODE_ARGS[@]}"; do
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
                    echo "Error: only one diff file path is allowed in review mode."
                    usage
                    exit 1
                fi
                DIFF_FILE="$arg"
                ;;
        esac
    done

    if [[ "$USE_MOCK" != true && -z "$DIFF_FILE" ]]; then
        echo "Error: review mode requires a diff file path, or use --use-mock."
        usage
        exit 1
    fi

    if [[ -n "$DIFF_FILE" ]]; then
        APP_ARGS+=("$DIFF_FILE")
    fi
    if [[ "$USE_MOCK" == true ]]; then
        APP_ARGS+=("--use-mock")
    fi
else
    if [[ ${#MODE_ARGS[@]} -gt 0 ]]; then
        APP_ARGS+=("${MODE_ARGS[@]}")
    fi
fi

TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Construct arguments: first --app.mode, then application-specific arguments
ALL_ARGUMENTS="--app.mode=$MODE"
if [[ -n "$ADDITIONAL_PROPERTIES_FILE" ]]; then
    ALL_ARGUMENTS="$ALL_ARGUMENTS --spring.config.additional-location=file:$ADDITIONAL_PROPERTIES_FILE"
fi

APP_ARGUMENTS="${APP_ARGS[*]}"
if [[ -n "$APP_ARGUMENTS" ]]; then
    ALL_ARGUMENTS="$ALL_ARGUMENTS $APP_ARGUMENTS"
fi

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$ALL_ARGUMENTS" \
    2>&1 | tee ../logs/spring-app-$TIMESTAMP.log
