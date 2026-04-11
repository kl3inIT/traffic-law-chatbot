import axios from 'axios';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8088',
  headers: { 'Content-Type': 'application/json' },
});

export function apiGet<T>(path: string): Promise<T> {
  return api.get<T>(path).then((r) => r.data);
}

export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return api.post<T>(path, body).then((r) => r.data);
}

export function apiPut<T>(path: string, body: unknown): Promise<T> {
  return api.put<T>(path, body).then((r) => r.data);
}

export function apiDelete(path: string): Promise<void> {
  return api.delete(path).then(() => undefined);
}

export function apiPostMultipart<T>(path: string, formData: FormData): Promise<T> {
  return api
    .post<T>(path, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((r) => r.data);
}
