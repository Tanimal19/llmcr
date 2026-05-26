source ../.env

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="./logs/system-test-$TIMESTAMP.log"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="--app.mode=api --config.path=./config.test.yml" \
    2>&1 | tee "$LOG_FILE"