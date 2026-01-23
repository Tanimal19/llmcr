source ../.env

SPRING_ARGUMENTS="--app.mode=evaluation --evaluation.input.pullrequest.path=../evaluation/data/pull_requests.json --evaluation.output.path=../evaluation/data/evaluation_results.json"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    2>&1 | tee spring-app.log
