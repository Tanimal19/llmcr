#!/bin/sh
MODEL="embeddinggemma:300m"

# Start Ollama in the background.
echo "starting ollama"
/bin/ollama serve &
pid=$!

# Pause for Ollama to start.
sleep 5

ollama pull $MODEL
echo "model pulled!"

# Wait for Ollama process to finish.
wait $pid