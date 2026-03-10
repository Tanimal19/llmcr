source ../.env

# SPRING_ARGUMENTS="--app.mode=etl \
#     --etl.input.javaproject.path=\"../_datasets/spring-ai-main simple\" \
#     --etl.input.document.paths=\"../_datasets/spring-ai-main simple/spring-ai-docs/src/main/antora/modules/ROOT/pages/,../_datasets/Effective Java.pdf\""

SPRING_ARGUMENTS="--app.mode=rag"

./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="$SPRING_ARGUMENTS" \
    2>&1 | tee spring-app.log
