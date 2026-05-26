export const delay = async (ms: number) =>
  new Promise<void>(resolve => {
    setTimeout(resolve, ms);
  });

export const setupMockFetch = (handler: (url: string, init?: RequestInit) => Response | Promise<Response>) => {
  globalThis.fetch = async (input: URL | RequestInfo, init?: RequestInit) => {
    let url: string;

    if (typeof input === 'string') {
      url = input;
    } else if (input instanceof URL) {
      url = input.toString();
    } else {
      url = input.url;
    }

    return handler(url, init);
  };
};

export const jsonResponse = (value: unknown, status = 200) =>
  Response.json(value, {
    status,
    headers: { 'content-type': 'application/json' },
  });

export const createSseResponse = (events: Array<{ event: string; data: unknown }>) => {
  const payload = events
    .map(({ event, data }) => {
      const encodedData = typeof data === 'string' ? data : JSON.stringify(data);
      return `event: ${event}\ndata: ${encodedData}`;
    })
    .join('\n\n');

  return new Response(payload, {
    status: 200,
    headers: { 'content-type': 'text/event-stream' },
  });
};
