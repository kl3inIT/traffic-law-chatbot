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
      {/* New thread button -- pinned at top, primary color per UI-SPEC */}
      <Button
        variant="default"
        className="w-full justify-start gap-2"
        onClick={() => router.push('/')}
      >
        <Plus className="h-4 w-4" />
        + Cuoc hoi thoai moi
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
            Khong the tai danh sach cuoc hoi thoai.
          </p>
        )}

        {threads && threads.length === 0 && (
          <div className="p-4 text-center">
            <p className="text-sm font-semibold">Chua co cuoc hoi thoai</p>
            <p className="text-xs text-muted-foreground mt-1">
              Bat dau cuoc hoi thoai moi de dat cau hoi ve luat giao thong.
            </p>
          </div>
        )}

        {threads?.map((thread) => {
          const isActive = thread.threadId === activeThreadId;
          const title = thread.firstMessage
            ? thread.firstMessage.length > 40
              ? thread.firstMessage.substring(0, 40) + '...'
              : thread.firstMessage
            : 'Cuoc hoi thoai moi';

          // Relative timestamp
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
  if (diffMin < 1) return 'vua xong';
  if (diffMin < 60) return `${diffMin} phut truoc`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `${diffHours} gio truoc`;
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays} ngay truoc`;
}
