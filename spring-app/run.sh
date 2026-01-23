source ../.env

SPRING_ARGUMENTS="--app.mode=etl"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    2>&1 | tee spring-app.log
