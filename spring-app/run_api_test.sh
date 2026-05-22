export BASE_URL=http://localhost:8080/api

curl -i "$BASE_URL/health"

echo ""
echo "Testing chat API..."
curl -i -X POST "$BASE_URL/chat" -H "Content-Type: application/json" -d '{"query":"What is VectorStore?"}'

echo ""
echo "Testing review API..."
curl -i -X POST "$BASE_URL/review" -H "Content-Type: application/json" -d '{"pullRequestJsonPath":"/absolute/path/to/pr.json"}'

echo ""
echo "Testing get RAG scope API..."
curl -i "$BASE_URL/rag-scope"

echo ""
echo "Testing set RAG scope API..."
curl -i -X POST "$BASE_URL/rag-scope" -H "Content-Type: application/json" -d '{"trackRootPaths":["../_datasets/projects/spring-ai-main/","../_datasets/projects/spring-ai-main/spring-ai-docs/src/main/antora/modules/ROOT/pages/"]}'

echo ""
echo "Testing list track roots API..."
curl -i "$BASE_URL/lsdb"

echo ""
echo "Testing sync API..."
time curl -i -X POST "$BASE_URL/sync"
time curl -i -X POST "$BASE_URL/sync/1"
