'use client';

import { useState } from 'react';
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  ChevronRight,
  Clock,
  Loader2,
  XCircle,
} from 'lucide-react';
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
import { useSourceIngestionJobs } from '@/hooks/use-sources';
import type { IngestionJobResponse, IngestionJobStatus, IngestionStep } from '@/types/api';

const PIPELINE_STAGES: IngestionStep[] = [
  'FETCH',
  'PARSE',
  'NORMALIZE',
  'CHUNK',
  'EMBED',
  'INDEX',
  'FINALIZE',
];

const stageLabels: Record<IngestionStep, string> = {
  FETCH: 'Tải về',
  PARSE: 'Phân tích',
  NORMALIZE: 'Chuẩn hóa',
  CHUNK: 'Chia đoạn',
  EMBED: 'Nhúng véc-tơ',
  INDEX: 'Lập chỉ mục',
  FINALIZE: 'Hoàn thành',
};

const statusBadgeConfig: Record<IngestionJobStatus, { label: string; className: string }> = {
  QUEUED: { label: 'Đang chờ', className: 'bg-yellow-100 text-yellow-800' },
  RUNNING: { label: 'Đang chạy', className: 'bg-blue-100 text-blue-800' },
  SUCCEEDED: { label: 'Thành công', className: 'bg-green-100 text-green-800' },
  FAILED: { label: 'Thất bại', className: 'bg-red-100 text-red-800' },
  CANCELLED: { label: 'Đã hủy', className: 'bg-gray-100 text-gray-600' },
  RETRYING: { label: 'Đang thử lại', className: 'bg-orange-100 text-orange-800' },
};

type StageState = 'done' | 'active' | 'pending' | 'failed';

function getStageState(stage: IngestionStep, job: IngestionJobResponse): StageState {
  const currentIdx = job.stepName ? PIPELINE_STAGES.indexOf(job.stepName) : -1;
  const stageIdx = PIPELINE_STAGES.indexOf(stage);

  if (job.status === 'FAILED') {
    if (stageIdx < currentIdx) return 'done';
    if (stageIdx === currentIdx) return 'failed';
    return 'pending';
  }
  if (job.status === 'SUCCEEDED') return 'done';
  if (stageIdx < currentIdx) return 'done';
  if (stageIdx === currentIdx) return 'active';
  return 'pending';
}

function StageIcon({ state }: { state: StageState }) {
  switch (state) {
    case 'done':
      return <CheckCircle2 className="h-4 w-4 text-green-500" />;
    case 'active':
      return <Loader2 className="h-4 w-4 animate-spin text-blue-500" />;
    case 'failed':
      return <XCircle className="h-4 w-4 text-red-500" />;
    case 'pending':
      return <ChevronRight className="text-muted-foreground h-4 w-4" />;
  }
}

function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN');
}

function PipelineTimeline({ job }: { job: IngestionJobResponse }) {
  return (
    <div className="space-y-1">
      {PIPELINE_STAGES.map((stage) => {
        const state = getStageState(stage, job);
        return (
          <div
            key={stage}
            className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm ${
              state === 'active' ? 'bg-blue-50' : state === 'failed' ? 'bg-red-50' : ''
            }`}
          >
            <StageIcon state={state} />
            <span
              className={
                state === 'done'
                  ? 'text-foreground'
                  : state === 'active'
                    ? 'font-medium text-blue-700'
                    : state === 'failed'
                      ? 'font-medium text-red-700'
                      : 'text-muted-foreground'
              }
            >
              {stageLabels[stage]}
            </span>
          </div>
        );
      })}
    </div>
  );
}

function JobCard({ job, isLatest }: { job: IngestionJobResponse; isLatest: boolean }) {
  const badge = statusBadgeConfig[job.status];
  return (
    <div
      className={`space-y-4 rounded-lg border p-4 ${isLatest ? 'border-primary/40 bg-primary/5' : ''}`}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="text-muted-foreground flex items-center gap-2 text-xs">
          <Clock className="h-3.5 w-3.5" />
          <span>Vào hàng: {formatDateTime(job.queuedAt)}</span>
        </div>
        <Badge className={badge.className}>{badge.label}</Badge>
      </div>

      {(job.status === 'RUNNING' || job.status === 'SUCCEEDED' || job.status === 'FAILED') && (
        <PipelineTimeline job={job} />
      )}

      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
        <div>
          <dt className="text-muted-foreground">Bắt đầu</dt>
          <dd>{formatDateTime(job.startedAt)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Kết thúc</dt>
          <dd>{formatDateTime(job.finishedAt)}</dd>
        </div>
        {job.retryCount > 0 && (
          <div>
            <dt className="text-muted-foreground">Số lần thử lại</dt>
            <dd>{job.retryCount}</dd>
          </div>
        )}
      </dl>

      {job.status === 'FAILED' && job.errorMessage && (
        <div className="space-y-1 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700">
          <div className="flex items-center gap-1.5 font-medium">
            <AlertCircle className="h-3.5 w-3.5" />
            Lỗi {job.errorCode ? `(${job.errorCode})` : ''}
          </div>
          <p className="break-words whitespace-pre-wrap">{job.errorMessage}</p>
        </div>
      )}
    </div>
  );
}

interface SourceIngestionDetailProps {
  sourceId: string;
  sourceTitle: string;
}

function SourceIngestionDetailContent({ sourceId, sourceTitle }: SourceIngestionDetailProps) {
  const { data: jobs, isLoading, isError } = useSourceIngestionJobs(sourceId, true);

  return (
    <>
      <SheetHeader className="border-b pb-4">
        <div className="flex items-center gap-2">
          <Activity className="text-primary h-5 w-5" />
          <SheetTitle>Tiến trình nhập liệu</SheetTitle>
        </div>
        <SheetDescription className="truncate">{sourceTitle}</SheetDescription>
      </SheetHeader>

      <div className="flex-1 space-y-4 overflow-y-auto p-4">
        {isLoading && (
          <div className="space-y-3">
            {[1, 2].map((i) => (
              <Skeleton key={i} className="h-32 w-full rounded-lg" />
            ))}
          </div>
        )}

        {isError && (
          <div className="text-muted-foreground py-8 text-center text-sm">
            Không thể tải dữ liệu. Vui lòng thử lại.
          </div>
        )}

        {!isLoading && !isError && (!jobs || jobs.length === 0) && (
          <div className="text-muted-foreground py-8 text-center text-sm">
            Chưa có tiến trình nhập liệu nào.
          </div>
        )}

        {jobs && jobs.map((job, idx) => <JobCard key={job.id} job={job} isLatest={idx === 0} />)}
      </div>
    </>
  );
}

export function SourceIngestionDetailButton({ sourceId, sourceTitle }: SourceIngestionDetailProps) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={() => setOpen(true)}>
        <Activity className="mr-1 h-3.5 w-3.5" />
        Tiến trình
      </Button>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="flex w-full flex-col p-0 sm:max-w-md">
          {open && <SourceIngestionDetailContent sourceId={sourceId} sourceTitle={sourceTitle} />}
        </SheetContent>
      </Sheet>
    </>
  );
}
