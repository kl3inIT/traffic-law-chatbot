'use client';

import { useRouter } from 'next/navigation';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useCheckRuns } from '@/hooks/use-check-runs';
import type { CheckRun, CheckRunStatus } from '@/types/api';

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

export default function CheckRunsPage() {
  const router = useRouter();
  const { data: runs, isLoading } = useCheckRuns();

  return (
    <div className="space-y-4 p-6">
      <h1 className="text-xl font-semibold">Lịch sử chạy kiểm tra</h1>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : (runs ?? []).length === 0 ? (
        <div className="py-8 text-center">
          <h2 className="text-xl font-semibold">Chưa có lần chạy kiểm tra</h2>
          <p className="text-muted-foreground mt-2 text-sm">
            Nhấn &apos;Chạy kiểm tra&apos; trong màn hình Định nghĩa kiểm tra để bắt đầu lần chạy đầu tiên.
          </p>
        </div>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-40">Ngày chạy</TableHead>
                <TableHead className="w-28">Điểm trung bình</TableHead>
                <TableHead>Bộ tham số</TableHead>
                <TableHead className="w-28">Số kiểm tra</TableHead>
                <TableHead className="w-32">Trạng thái</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(runs ?? []).map((run: CheckRun) => (
                <TableRow
                  key={run.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => router.push(`/checks/runs/${run.id}`)}
                >
                  <TableCell className="text-sm">{formatDate(run.createdDate)}</TableCell>
                  <TableCell>
                    {run.averageScore !== null ? (
                      <Badge variant="outline" className={scoreClass(run.averageScore)}>
                        {Math.round(run.averageScore * 100)}%
                      </Badge>
                    ) : (
                      <span className="text-muted-foreground text-sm">—</span>
                    )}
                  </TableCell>
                  <TableCell className="text-sm">{run.parameterSetName ?? '—'}</TableCell>
                  <TableCell className="text-sm">{run.checkCount ?? '—'}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className={statusClass[run.status]}>
                      {statusLabel[run.status]}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
