source ../.env

SPRING_ARGUMENTS="--app.mode=evaluation --evaluation.input.path=../evaluation/pull_requests.json"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    2>&1 | tee spring-app.log
