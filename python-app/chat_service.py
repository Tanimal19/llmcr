from google import genai
from typing import List, Dict
from config import Config


class ChatService:
    def __init__(self):
        self.client = genai.Client(api_key=Config.GEMINI_API_KEY)

    def generate_response(
        self, query: str, context_documents: List[Dict]
    ) -> str | None:
        context = self._build_context(context_documents)
        prompt = self._create_prompt(query, context)

        try:
            response = self.client.models.generate_content(
                model=Config.CHAT_MODEL,
                contents=prompt,
            )
            return response.text
        except Exception as e:
            print(f"Error generating response: {str(e)}")
            return None

    def _build_context(self, documents: List[Dict]) -> str:
        if not documents:
            return "No relevant context found."

        context_parts = []
        for i, doc in enumerate(documents, 1):
            content = doc.get("content", "")
            metadata = doc.get("metadata", {})

            if metadata:
                metadata_str = ", ".join([f"{k}: {v}" for k, v in metadata.items()])
                context_parts.append(f"[Document {i}] ({metadata_str})\n{content}")
            else:
                context_parts.append(f"[Document {i}]\n{content}")

        return "\n\n".join(context_parts)

    def _create_prompt(self, query: str, context: str) -> str:
        prompt = f""" You are a knowledgeable java engineer. Given the following context and question, provide a detailed and accurate answer.

Context:
{context}

Question: {query}

Instructions:
- Answer the question based primarily on the provided context
- If the context doesn't contain enough information to answer the question, say so
- Be concise but comprehensive

Answer:"""

        return prompt

    def generate_simple_response(self, prompt: str) -> str | None:
        try:
            response = self.client.models.generate_content(
                model=Config.CHAT_MODEL,
                contents=prompt,
            )
            return response.text
        except Exception as e:
            print(f"Error generating response: {str(e)}")
            return None
