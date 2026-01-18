source ../.env

SPRING_ARGUMENTS="--app.mode=etl"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    > spring-app-etl.log 2>&1
