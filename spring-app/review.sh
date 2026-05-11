source ../.env

# please refer to runner/ to see available modes
# Usage: ./review.sh <diff-file-path>
DIFF_FILE="${1:?Usage: ./review.sh <diff-file-path>}"
SPRING_ARGUMENTS="--app.mode=review $DIFF_FILE"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    2>&1 | tee ../logs/spring-app-$TIMESTAMP.log
