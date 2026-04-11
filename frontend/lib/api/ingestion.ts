import { apiPost, apiPostMultipart } from './client';
import type { IngestionAcceptedResponse } from '@/types/api';

export interface UploadSourcePayload {
  file: File;
  title: string;
  publisherName?: string;
  createdBy?: string;
}

export interface UrlSourcePayload {
  url: string;
  title?: string;
  publisherName?: string;
  createdBy?: string;
}

export function uploadSource(payload: UploadSourcePayload): Promise<IngestionAcceptedResponse> {
  const formData = new FormData();
  formData.append('file', payload.file);
  // metadata part must be JSON blob with correct content-type
  const metadata = JSON.stringify({
    title: payload.title,
    publisherName: payload.publisherName ?? '',
    createdBy: payload.createdBy ?? 'admin',
  });
  formData.append('metadata', new Blob([metadata], { type: 'application/json' }));
  return apiPostMultipart('/api/v1/admin/sources/upload', formData);
}

export function submitUrl(payload: UrlSourcePayload): Promise<IngestionAcceptedResponse> {
  return apiPost('/api/v1/admin/sources/url', {
    url: payload.url,
    title: payload.title ?? payload.url,
    publisherName: payload.publisherName ?? '',
    createdBy: payload.createdBy ?? 'admin',
  });
}
