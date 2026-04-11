'use client';

import { useRouter, useParams } from 'next/navigation';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Skeleton } from '@/components/ui/skeleton';
import { useThreads } from '@/hooks/use-threads';
import { cn } from '@/lib/utils';

export function ThreadList() {
  const router = useRouter();
  const params = useParams();
  const activeThreadId = params?.id as string | undefined;
  const { data: threads, isLoading, isError } = useThreads();

  return (
    <div className="flex flex-col gap-2">
      <Button
        variant="default"
        className="w-full justify-start gap-2"
        onClick={() => router.push('/')}
      >
        <Plus className="h-4 w-4" />
        Cuộc hội thoại mới
      </Button>

      <ScrollArea className="h-[calc(100vh-200px)]">
        {isLoading && (
          <div className="flex flex-col gap-2 p-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        )}

        {isError && (
          <p className="text-xs text-muted-foreground p-2">
            Không thể tải danh sách cuộc hội thoại.
          </p>
        )}

        {threads && threads.length === 0 && (
          <div className="p-4 text-center">
            <p className="text-sm font-semibold">Chưa có cuộc hội thoại</p>
            <p className="text-xs text-muted-foreground mt-1">
              Bắt đầu cuộc hội thoại mới để đặt câu hỏi về luật giao thông.
            </p>
          </div>
        )}

        {threads?.map((thread) => {
          const isActive = thread.threadId === activeThreadId;
          const title = thread.firstMessage
            ? thread.firstMessage.length > 40
              ? thread.firstMessage.substring(0, 40) + '...'
              : thread.firstMessage
            : 'Cuộc hội thoại mới';

          const timeAgo = formatRelativeTime(thread.updatedAt);

          return (
            <button
              key={thread.threadId}
              onClick={() => router.push(`/threads/${thread.threadId}`)}
              className={cn(
                'w-full text-left p-2 rounded-md text-sm hover:bg-muted transition-colors',
                isActive && 'bg-primary/10 border-l-2 border-primary'
              )}
            >
              <div className="truncate">{title}</div>
              <div className="text-xs text-muted-foreground">{timeAgo}</div>
            </button>
          );
        })}
      </ScrollArea>
    </div>
  );
}

function formatRelativeTime(isoDate: string): string {
  const now = new Date();
  const date = new Date(isoDate);
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return 'vừa xong';
  if (diffMin < 60) return `${diffMin} phút trước`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `${diffHours} giờ trước`;
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays} ngày trước`;
}
