import numpy as np
from google import genai
from typing import List, Union
from config import Config


class EmbeddingService:
    def __init__(self):
        self.client = genai.Client(api_key=Config.GEMINI_API_KEY)

    def encode(self, text: str):
        try:
            result = self.client.models.embed_content(
                model=Config.EMBEDDING_MODEL, contents=text
            )
            return result.embeddings
        except Exception as e:
            print(f"Error generating embedding: {e}")
            return None
