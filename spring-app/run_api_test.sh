export BASE_URL=http://localhost:8081/api

curl -i "$BASE_URL/health"

echo -e "\nTesting chat API..."
curl -i -X POST "$BASE_URL/chat" -H "Content-Type: application/json" -d '{"query":"What is VectorStore?"}'

echo -e "\nTesting set RAG scope API..."
curl -i -X POST "$BASE_URL/setrag" -H "Content-Type: application/json" -d '{"trackRootPaths":["../_datasets/projects/spring-ai-main/","../_datasets/projects/spring-ai-main/spring-ai-docs/src/main/antora/modules/ROOT/pages/"]}'

echo -e "\nTesting list track roots API..."
curl -i "$BASE_URL/lsdb"

echo -e "\nTesting sync API..."
curl -i -X POST "$BASE_URL/sync"

echo -e "\nTesting review API..."
curl -i -X POST "$BASE_URL/review" -H "Content-Type: application/json" -d '{"pullRequestJsonPath":"/absolute/path/to/pr.json"}'