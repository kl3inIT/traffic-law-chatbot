'use client';

import { useParams, useRouter } from 'next/navigation';
import { ChevronLeft } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useChatLogDetail } from '@/hooks/use-chat-logs';
import type { GroundingStatus } from '@/types/api';

function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

const groundingStatusLabel: Record<GroundingStatus, string> = {
  GROUNDED: 'Đã dẫn nguồn',
  LIMITED_GROUNDING: 'Dẫn nguồn hạn chế',
  REFUSED: 'Từ chối trả lời',
};

const groundingStatusClass: Record<GroundingStatus, string> = {
  GROUNDED: 'bg-green-100 text-green-800 border-green-200',
  LIMITED_GROUNDING: 'bg-yellow-100 text-yellow-800 border-yellow-200',
  REFUSED: 'bg-red-100 text-red-800 border-red-200',
};

export default function ChatLogDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { data: log, isLoading, isError } = useChatLogDetail(params.id);

  if (isLoading) {
    return (
      <div className="max-w-4xl space-y-6 p-6">
        <Skeleton className="h-8 w-32" />
        <Skeleton className="h-6 w-64" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (isError || !log) {
    return (
      <div className="space-y-4 p-6">
        <Button variant="ghost" size="sm" onClick={() => router.back()}>
          <ChevronLeft size={16} />
          Quay lại danh sách
        </Button>
        <p className="text-muted-foreground text-sm">Không tìm thấy bản ghi.</p>
      </div>
    );
  }

  const sources = log.sources ? log.sources.split(', ').filter((s) => s.trim()) : [];

  const totalTokens = log.promptTokens + log.completionTokens;

  return (
    <div className="max-w-4xl space-y-6 p-6">
      <Button variant="ghost" size="sm" onClick={() => router.back()}>
        <ChevronLeft size={16} />
        Quay lại danh sách
      </Button>

      <div className="flex flex-wrap items-center gap-3">
        <span className="text-muted-foreground text-sm">{formatDate(log.createdDate)}</span>
        <Badge variant="outline" className={groundingStatusClass[log.groundingStatus]}>
          {groundingStatusLabel[log.groundingStatus]}
        </Badge>
        {log.conversationId && (
          <span className="text-muted-foreground text-xs">ID hội thoại: {log.conversationId}</span>
        )}
      </div>

      <Card className="space-y-2 p-4">
        <p className="text-muted-foreground text-sm font-medium">Câu hỏi</p>
        <p className="text-sm">{log.question}</p>
      </Card>

      <Card className="space-y-2 p-4">
        <p className="text-muted-foreground text-sm font-medium">Câu trả lời</p>
        <p className="text-sm whitespace-pre-wrap">{log.answer}</p>
      </Card>

      <Card className="space-y-2 p-4">
        <p className="text-muted-foreground text-sm font-medium">Nguồn trích dẫn</p>
        {sources.length > 0 ? (
          <ul className="list-inside list-disc space-y-1">
            {sources.map((src, i) => (
              <li key={i} className="text-sm">
                {src}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-muted-foreground text-sm">Không có nguồn trích dẫn</p>
        )}
      </Card>

      {log.pipelineLog && (
        <Card className="space-y-2 p-4">
          <p className="text-muted-foreground text-sm font-medium">Pipeline log</p>
          <pre className="text-muted-foreground bg-muted overflow-x-auto rounded p-3 font-mono text-xs whitespace-pre-wrap">
            {log.pipelineLog}
          </pre>
        </Card>
      )}

      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div>
          <dt className="text-muted-foreground text-xs">Prompt tokens</dt>
          <dd className="text-sm font-medium">{log.promptTokens}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground text-xs">Completion tokens</dt>
          <dd className="text-sm font-medium">{log.completionTokens}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground text-xs">Tổng tokens</dt>
          <dd className="text-sm font-medium">{totalTokens}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground text-xs">Thời gian phản hồi</dt>
          <dd className="text-sm font-medium">{log.responseTime} ms</dd>
        </div>
      </dl>
    </div>
  );
}
