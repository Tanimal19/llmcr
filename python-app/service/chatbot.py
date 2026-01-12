import sys
from typing import Optional
from google import genai
from google.genai import types
from config import Config


class ChatbotService:
    def __init__(self):
        try:
            # Initialize Gemini client
            self.client = genai.Client(api_key=Config.GEMINI_API_KEY)
            self.model_name = Config.CHAT_MODEL

            print(f"Initialized Gemini chatbot with model: {self.model_name}")

        except Exception as e:
            print(f"Error initializing Gemini chatbot: {e}")
            sys.exit(1)

    def chat(self, prompt: str) -> str:
        try:
            response = self.client.models.generate_content(
                model=self.model_name,
                contents=prompt,
                config=types.GenerateContentConfig(
                    temperature=0.7,
                ),
            )

            return response.text if response.text else ""

        except Exception as e:
            print(f"Error generating response: {e}")
            return ""
