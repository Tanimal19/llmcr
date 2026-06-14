source ../.env

usage() {
    echo "Usage: ./run.sh [mode] [mode-specific-arguments] -a [additional-properties-file]"
    echo ""
    echo "Modes:"
    echo "  api: run API server and keep listening for requests."
    echo "    ./run.sh api"
    echo ""
    echo "  chat: run ChatRunner."
    echo "    ./run.sh chat"
    echo ""
    echo "  sync: synchronize database with local datasets."
    echo ""
    echo "  review: review code changes based on pull request json/jsonl files."
    echo "    ./run.sh review (use default mock data)"
    echo "    ./run.sh review <pr-file.json>"
    echo "    ./run.sh review <pr-file.jsonl>"
    echo "    ./run.sh review <pr-file.jsonl> --jsonl-index 3"
    echo "    ./run.sh review <pr-file.json> --single-agent"
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

while [[ $# -gt 0 ]]; do
    case "$1" in
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
    PR_FILE=""
    JSONL_INDEX=""
    EXPECT_JSONL_INDEX_VALUE=false

    for arg in "${MODE_ARGS[@]}"; do
        if [[ "$EXPECT_JSONL_INDEX_VALUE" == true ]]; then
            JSONL_INDEX="$arg"
            EXPECT_JSONL_INDEX_VALUE=false
            continue
        fi

        case "$arg" in
            --jsonl-index)
                EXPECT_JSONL_INDEX_VALUE=true
                ;;
            --jsonl-index=*)
                JSONL_INDEX="${arg#--jsonl-index=}"
                ;;
            --single-agent)
                APP_ARGS+=("--single-agent")
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                if [[ -n "$PR_FILE" ]]; then
                    echo "Error: only one pr file path is allowed in review mode."
                    usage
                    exit 1
                fi
                PR_FILE="$arg"
                ;;
        esac
    done

    if [[ "$EXPECT_JSONL_INDEX_VALUE" == true ]]; then
        echo "Error: --jsonl-index requires a value."
        usage
        exit 1
    fi

    if [[ -n "$JSONL_INDEX" && -z "$PR_FILE" ]]; then
        echo "Error: --jsonl-index requires a .jsonl file path."
        usage
        exit 1
    fi

    if [[ -n "$JSONL_INDEX" && "$PR_FILE" != *.jsonl ]]; then
        echo "Error: --jsonl-index can only be used with .jsonl file."
        usage
        exit 1
    fi

    if [[ -n "$PR_FILE" ]]; then
        APP_ARGS+=("$PR_FILE")
    fi
    if [[ -n "$JSONL_INDEX" ]]; then
        APP_ARGS+=("--jsonl-index=$JSONL_INDEX")
    fi
else
    if [[ ${#MODE_ARGS[@]} -gt 0 ]]; then
        APP_ARGS+=("${MODE_ARGS[@]}")
    fi
fi

TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Construct arguments: first --app.mode, then application-specific arguments
ALL_ARGUMENTS="--app.mode=$MODE"
if [[ "$MODE" != "api" ]]; then
    ALL_ARGUMENTS="$ALL_ARGUMENTS --spring.main.web-application-type=none"
fi
if [[ -n "$ADDITIONAL_PROPERTIES_FILE" ]]; then
    ALL_ARGUMENTS="$ALL_ARGUMENTS"
fi

APP_ARGUMENTS="${APP_ARGS[*]}"
if [[ -n "$APP_ARGUMENTS" ]]; then
    ALL_ARGUMENTS="$ALL_ARGUMENTS $APP_ARGUMENTS"
fi

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$ALL_ARGUMENTS" \
    2>&1 | tee ./logs/spring-app-$TIMESTAMP.log
