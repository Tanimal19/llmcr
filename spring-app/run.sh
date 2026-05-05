source ../.env

# please refer to runner/ to see available modes
SPRING_ARGUMENTS="--app.mode=review"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    2>&1 | tee ../logs/spring-app-$TIMESTAMP.log
