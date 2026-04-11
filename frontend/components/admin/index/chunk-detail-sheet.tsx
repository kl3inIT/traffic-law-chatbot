'use client';

import { useState } from 'react';
import { Copy, Check } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useChunk } from '@/hooks/use-index';

interface ChunkDetailSheetProps {
  chunkId: string | null;
  onClose: () => void;
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore
    }
  };

  return (
    <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={handleCopy}>
      {copied ? (
        <Check className="mr-1 h-3.5 w-3.5 text-green-500" />
      ) : (
        <Copy className="mr-1 h-3.5 w-3.5" />
      )}
      {copied ? 'Đã sao chép' : 'Sao chép'}
    </Button>
  );
}

function ChunkDetailContent({ chunkId }: { chunkId: string }) {
  const { data: chunk, isLoading, isError } = useChunk(chunkId, true);

  if (isLoading) {
    return (
      <div className="space-y-4 p-4">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (isError || !chunk) {
    return (
      <div className="text-muted-foreground p-4 py-8 text-center text-sm">
        Không thể tải dữ liệu đoạn văn bản. Vui lòng thử lại.
      </div>
    );
  }

  const metadataJson = JSON.stringify(
    {
      sourceId: chunk.sourceId,
      sourceVersionId: chunk.sourceVersionId,
      chunkOrdinal: chunk.chunkOrdinal,
      pageNumber: chunk.pageNumber,
      sectionRef: chunk.sectionRef,
      contentHash: chunk.contentHash,
      processingVersion: chunk.processingVersion,
      approvalState: chunk.approvalState,
      trusted: chunk.trusted,
      active: chunk.active,
      origin: chunk.origin,
    },
    null,
    2,
  );

  const embeddingStr = chunk.embedding ? `[${chunk.embedding.join(', ')}]` : '';

  return (
    <div className="flex h-full flex-col">
      <SheetHeader className="border-b px-4 pb-4">
        <SheetTitle className="font-mono text-sm break-all">{chunk.id}</SheetTitle>
        <SheetDescription className="flex flex-wrap gap-2">
          <Badge variant="secondary">Thứ tự: {chunk.chunkOrdinal}</Badge>
          <Badge variant="secondary">Trang: {chunk.pageNumber}</Badge>
          {chunk.approvalState && <Badge variant="outline">{chunk.approvalState}</Badge>}
          {chunk.vectorDimension > 0 && (
            <Badge variant="outline">{chunk.vectorDimension} chiều</Badge>
          )}
        </SheetDescription>
      </SheetHeader>

      <ScrollArea className="flex-1 p-4">
        <div className="space-y-5">
          {/* Content */}
          <section>
            <div className="mb-1 flex items-center justify-between">
              <h3 className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
                Nội dung
              </h3>
              {chunk.content && <CopyButton text={chunk.content} />}
            </div>
            <pre className="bg-muted max-h-48 overflow-y-auto rounded-md p-3 text-xs leading-relaxed break-words whitespace-pre-wrap">
              {chunk.content ?? '(trống)'}
            </pre>
          </section>

          {/* Metadata */}
          <section>
            <div className="mb-1 flex items-center justify-between">
              <h3 className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
                Siêu dữ liệu
              </h3>
              <CopyButton text={metadataJson} />
            </div>
            <pre className="bg-muted max-h-48 overflow-y-auto rounded-md p-3 text-xs leading-relaxed break-words whitespace-pre-wrap">
              {metadataJson}
            </pre>
          </section>

          {/* Embedding */}
          <section>
            <div className="mb-1 flex items-center justify-between">
              <h3 className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
                Véc-tơ nhúng ({chunk.vectorDimension} chiều)
              </h3>
              {embeddingStr && <CopyButton text={embeddingStr} />}
            </div>
            {chunk.embedding ? (
              <pre className="bg-muted max-h-64 overflow-y-auto rounded-md p-3 font-mono text-xs leading-relaxed break-all whitespace-pre-wrap">
                {embeddingStr}
              </pre>
            ) : (
              <p className="text-muted-foreground text-xs italic">Không có dữ liệu véc-tơ.</p>
            )}
          </section>
        </div>
      </ScrollArea>
    </div>
  );
}

export function ChunkDetailSheet({ chunkId, onClose }: ChunkDetailSheetProps) {
  return (
    <Sheet
      open={!!chunkId}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <SheetContent side="right" className="flex w-full flex-col p-0 sm:max-w-lg">
        {chunkId && <ChunkDetailContent chunkId={chunkId} />}
      </SheetContent>
    </Sheet>
  );
}
