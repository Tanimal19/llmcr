import { apiRequest, requireNonBlank } from '#api/client.js';

export type ChatResponse = {
  answer: string;
  retrievedContexts: Record<string, number>;
};

export async function chat(query: string): Promise<ChatResponse> {
  requireNonBlank(query, 'query must not be blank');
  return apiRequest<ChatResponse>('/chat', {
    method: 'POST',
    body: JSON.stringify({ query }),
  });
}
