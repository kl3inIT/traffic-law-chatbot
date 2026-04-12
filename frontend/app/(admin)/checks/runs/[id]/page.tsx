'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { ChevronLeft } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useCheckRunDetail, useCheckRunResults } from '@/hooks/use-check-runs';
import type { CheckResult, CheckRunStatus } from '@/types/api';

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

function scoreClass(score: number): string {
  if (score >= 0.8) return 'bg-green-100 text-green-800 border-green-200';
  if (score >= 0.5) return 'bg-yellow-100 text-yellow-800 border-yellow-200';
  return 'bg-red-100 text-red-800 border-red-200';
}

const statusClass: Record<CheckRunStatus, string> = {
  RUNNING: 'bg-yellow-100 text-yellow-800 border-yellow-200',
  COMPLETED: 'bg-green-100 text-green-800 border-green-200',
  FAILED: 'bg-red-100 text-red-800 border-red-200',
};

const statusLabel: Record<CheckRunStatus, string> = {
  RUNNING: 'Đang chạy',
  COMPLETED: 'Hoàn thành',
  FAILED: 'Thất bại',
};

export default function CheckRunDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [selectedResult, setSelectedResult] = useState<CheckResult | null>(null);

  const { data: run, isLoading: runLoading } = useCheckRunDetail(params.id);
  const { data: results, isLoading: resultsLoading } = useCheckRunResults(params.id);

  const isLoading = runLoading || resultsLoading;

  if (isLoading) {
    return (
      <div className="p-6 space-y-6">
        <Skeleton className="h-8 w-32" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (!run) {
    return (
      <div className="p-6 space-y-4">
        <Button variant="ghost" size="sm" onClick={() => router.back()}>
          <ChevronLeft size={16} />
          Quay lại danh sách
        </Button>
        <p className="text-sm text-muted-foreground">Không tìm thấy bản ghi.</p>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <Button variant="ghost" size="sm" onClick={() => router.back()}>
        <ChevronLeft size={16} />
        Quay lại danh sách
      </Button>

      <Card className="p-4">
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p className="text-xs text-muted-foreground">Ngày chạy</p>
            <p className="text-sm font-medium">{formatDate(run.createdDate)}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Điểm trung bình</p>
            {run.averageScore !== null ? (
              <Badge variant="outline" className={scoreClass(run.averageScore)}>
                {Math.round(run.averageScore * 100)}%
              </Badge>
            ) : (
              <p className="text-sm font-medium">—</p>
            )}
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Bộ tham số</p>
            <p className="text-sm font-medium">{run.parameterSetName ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Số kiểm tra</p>
            <p className="text-sm font-medium">{run.checkCount ?? '—'}</p>
          </div>
        </div>
        <div className="mt-3">
          <Badge variant="outline" className={statusClass[run.status]}>
            {statusLabel[run.status]}
          </Badge>
        </div>
      </Card>

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-48">Câu hỏi</TableHead>
              <TableHead className="w-48">Câu trả lời tham chiếu</TableHead>
              <TableHead className="w-48">Câu trả lời thực tế</TableHead>
              <TableHead className="w-24">Điểm</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(results ?? []).map((result: CheckResult) => (
              <TableRow
                key={result.id}
                className="cursor-pointer hover:bg-muted/50"
                onClick={() => setSelectedResult(result)}
              >
                <TableCell>
                  <span className="truncate max-w-[180px] block text-sm">{result.question}</span>
                </TableCell>
                <TableCell>
                  <span className="truncate max-w-[180px] block text-sm">{result.referenceAnswer}</span>
                </TableCell>
                <TableCell>
                  <span className="truncate max-w-[180px] block text-sm">
                    {result.actualAnswer ?? '—'}
                  </span>
                </TableCell>
                <TableCell>
                  {result.score !== null ? (
                    <Badge variant="outline" className={scoreClass(result.score)}>
                      {result.score.toFixed(2)}
                    </Badge>
                  ) : (
                    <span className="text-muted-foreground text-sm">—</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {(results ?? []).length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="text-center text-muted-foreground h-24">
                  Chưa có kết quả kiểm tra.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <Sheet open={selectedResult !== null} onOpenChange={(open) => { if (!open) setSelectedResult(null); }}>
        <SheetContent className="w-[480px] sm:max-w-[480px]">
          <SheetHeader>
            <SheetTitle className="text-sm font-medium line-clamp-2">
              {selectedResult?.question.slice(0, 60)}
              {(selectedResult?.question.length ?? 0) > 60 ? '...' : ''}
            </SheetTitle>
          </SheetHeader>
          <ScrollArea className="h-full mt-4 pr-2">
            <div className="space-y-3 pb-8">
              <Card className="p-4">
                <p className="text-xs text-muted-foreground mb-2">Câu hỏi</p>
                <p className="text-sm">{selectedResult?.question}</p>
              </Card>
              <Card className="p-4">
                <p className="text-xs text-muted-foreground mb-2">Câu trả lời tham chiếu</p>
                <p className="text-sm whitespace-pre-wrap">{selectedResult?.referenceAnswer}</p>
              </Card>
              <Card className="p-4">
                <p className="text-xs text-muted-foreground mb-2">Câu trả lời thực tế</p>
                <p className="text-sm whitespace-pre-wrap">
                  {selectedResult?.actualAnswer ?? 'Không có câu trả lời'}
                </p>
              </Card>
            </div>
          </ScrollArea>
        </SheetContent>
      </Sheet>
    </div>
  );
}
